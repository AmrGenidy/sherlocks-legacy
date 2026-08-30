package server;

import common.WireLimits;
import common.dto.pinboard.PinboardItemDTO;
import common.dto.pinboard.PinboardLinkDTO;
import common.dto.pinboard.PinboardStateDTO;
import common.dto.pinboard.PinboardUpdateDTO;

/**
 * Server-side validation for client pinboard updates (security-pass issue 02). The pinboard is
 * shared state mutated directly from the wire, so every update is checked BEFORE it is broadcast
 * to the other player or applied to the session's reducer: string lengths, finite/bounded
 * geometry (NaN or huge coordinates would poison the peer's renderer), and growth caps on items,
 * links and template entries.
 */
final class PinboardUpdateValidator {

  private PinboardUpdateValidator() {}

  /** Whether {@code update} may be applied to (and broadcast for) the given board state. */
  static boolean isAcceptable(PinboardUpdateDTO update, PinboardStateDTO state) {
    if (update == null || update.getType() == null) {
      return false;
    }
    if (!fits(update.getTargetId(), WireLimits.MAX_ID_LENGTH)
        || !fits(update.getKey(), WireLimits.MAX_ID_LENGTH)
        || !fits(update.getValue(), WireLimits.MAX_NOTE_TEXT_LENGTH)) {
      return false;
    }

    switch (update.getType()) {
      case ADD_ITEM:
        return update.getItem() != null
            && isValidItem(update.getItem())
            && itemCount(state) < WireLimits.MAX_PINBOARD_ITEMS;
      case RESIZE_ITEM:
        return update.getItem() == null || isValidItem(update.getItem());
      case MOVE_ITEM:
        return boundedCoord(update.getNewX()) && boundedCoord(update.getNewY());
      case ADD_LINK:
        return update.getLink() != null
            && isValidLink(update.getLink())
            && linkCount(state) < WireLimits.MAX_PINBOARD_LINKS;
      case REMOVE_LINK:
        return update.getLink() == null || isValidLink(update.getLink());
      case UPDATE_TEMPLATE_NOTE:
        return update.getKey() == null
            || templateCount(state) < WireLimits.MAX_PINBOARD_TEMPLATE_ENTRIES
            || templateHasKey(state, update.getKey());
      default:
        // UPDATE_CONTENT / REMOVE_ITEM / UPDATE_TEMPLATE_DROP / CLEAR_BOARD: the shared field
        // checks above are sufficient.
        return true;
    }
  }

  private static boolean isValidItem(PinboardItemDTO item) {
    return fits(item.getId(), WireLimits.MAX_ID_LENGTH)
        && fits(item.getType(), WireLimits.MAX_NAME_LENGTH)
        && fits(item.getTitle(), WireLimits.MAX_NAME_LENGTH * 2)
        && fits(item.getContent(), WireLimits.MAX_NOTE_TEXT_LENGTH)
        && fits(item.getRelatedJournalEntryId(), WireLimits.MAX_ID_LENGTH)
        && fits(item.getColor(), WireLimits.MAX_NAME_LENGTH)
        && fits(item.getAuthor(), WireLimits.MAX_NAME_LENGTH)
        && boundedCoord(item.getX())
        && boundedCoord(item.getY())
        && boundedSize(item.getWidth())
        && boundedSize(item.getHeight());
  }

  private static boolean isValidLink(PinboardLinkDTO link) {
    return fits(link.getStartItemId(), WireLimits.MAX_ID_LENGTH)
        && fits(link.getEndItemId(), WireLimits.MAX_ID_LENGTH)
        && fits(link.getColor(), WireLimits.MAX_NAME_LENGTH);
  }

  private static boolean fits(String value, int max) {
    return value == null || value.length() <= max;
  }

  private static boolean boundedCoord(double v) {
    return Double.isFinite(v) && Math.abs(v) <= WireLimits.MAX_PINBOARD_COORD;
  }

  private static boolean boundedSize(double v) {
    return Double.isFinite(v) && v >= 0 && v <= WireLimits.MAX_PINBOARD_COORD;
  }

  private static int itemCount(PinboardStateDTO state) {
    return state == null || state.getItems() == null ? 0 : state.getItems().size();
  }

  private static int linkCount(PinboardStateDTO state) {
    return state == null || state.getLinks() == null ? 0 : state.getLinks().size();
  }

  private static int templateCount(PinboardStateDTO state) {
    return state == null || state.getTemplateData() == null ? 0 : state.getTemplateData().size();
  }

  private static boolean templateHasKey(PinboardStateDTO state, String key) {
    return state != null
        && state.getTemplateData() != null
        && state.getTemplateData().containsKey(key);
  }
}
