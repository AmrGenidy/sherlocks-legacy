package ui.pinboard;

import common.dto.pinboard.PinboardItemDTO;
import common.dto.pinboard.PinboardLinkDTO;
import common.dto.pinboard.PinboardStateDTO;
import common.dto.pinboard.PinboardUpdateDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure diff between two pinboard snapshots for undo/redo (.scratch/gui-pinboard-undo): emits the
 * granular {@link PinboardUpdateDTO}s that transform {@code from} into {@code to}. A restore
 * replays these through the SAME path a live edit takes — applied locally like an incoming peer
 * update and broadcast via the normal update callback — so in multiplayer the peer's board and the
 * server's state reducer follow every undo instead of silently diverging, and an undo costs the
 * same wire budget as the edit it reverts. Order matters: threads whose endpoints vanish are cut
 * before their cards, and threads are only (re)strung after both endpoints exist again. A link is
 * immutable once pinned, so a colour difference is modelled as cut + re-string. Pure — no JavaFX —
 * so it is unit-testable.
 */
final class PinboardUndoDiff {

  private PinboardUndoDiff() {}

  static List<PinboardUpdateDTO> diff(PinboardStateDTO from, PinboardStateDTO to) {
    List<PinboardUpdateDTO> out = new ArrayList<>();
    Map<String, PinboardItemDTO> fromItems = itemsById(from);
    Map<String, PinboardItemDTO> toItems = itemsById(to);
    Map<String, PinboardLinkDTO> fromLinks = linksByKey(from);
    Map<String, PinboardLinkDTO> toLinks = linksByKey(to);

    for (Map.Entry<String, PinboardLinkDTO> e : fromLinks.entrySet()) {
      PinboardLinkDTO target = toLinks.get(e.getKey());
      if (target == null || !Objects.equals(e.getValue().getColor(), target.getColor())) {
        PinboardUpdateDTO u = new PinboardUpdateDTO(PinboardUpdateDTO.UpdateType.REMOVE_LINK);
        u.setLink(e.getValue());
        out.add(u);
      }
    }

    for (PinboardItemDTO item : fromItems.values()) {
      if (!toItems.containsKey(item.getId())) {
        PinboardUpdateDTO u = new PinboardUpdateDTO(PinboardUpdateDTO.UpdateType.REMOVE_ITEM);
        u.setTargetId(item.getId());
        out.add(u);
      }
    }

    for (PinboardItemDTO target : toItems.values()) {
      PinboardItemDTO current = fromItems.get(target.getId());
      if (current == null) {
        PinboardUpdateDTO u = new PinboardUpdateDTO(PinboardUpdateDTO.UpdateType.ADD_ITEM);
        u.setItem(target);
        out.add(u);
        continue;
      }
      if (current.getX() != target.getX() || current.getY() != target.getY()) {
        PinboardUpdateDTO u = new PinboardUpdateDTO(PinboardUpdateDTO.UpdateType.MOVE_ITEM);
        u.setTargetId(target.getId());
        u.setNewX(target.getX());
        u.setNewY(target.getY());
        out.add(u);
      }
      if (current.getWidth() != target.getWidth() || current.getHeight() != target.getHeight()) {
        PinboardUpdateDTO u = new PinboardUpdateDTO(PinboardUpdateDTO.UpdateType.RESIZE_ITEM);
        u.setTargetId(target.getId());
        u.setItem(target);
        out.add(u);
      }
      if (!Objects.equals(current.getContent(), target.getContent())) {
        PinboardUpdateDTO u = new PinboardUpdateDTO(PinboardUpdateDTO.UpdateType.UPDATE_CONTENT);
        u.setTargetId(target.getId());
        u.setValue(target.getContent());
        out.add(u);
      }
    }

    for (Map.Entry<String, PinboardLinkDTO> e : toLinks.entrySet()) {
      PinboardLinkDTO current = fromLinks.get(e.getKey());
      if (current == null || !Objects.equals(current.getColor(), e.getValue().getColor())) {
        PinboardUpdateDTO u = new PinboardUpdateDTO(PinboardUpdateDTO.UpdateType.ADD_LINK);
        u.setLink(e.getValue());
        out.add(u);
      }
    }
    return out;
  }

  /** Keyed by id; insertion-ordered so re-added cards come back in their snapshot order. */
  private static Map<String, PinboardItemDTO> itemsById(PinboardStateDTO state) {
    Map<String, PinboardItemDTO> byId = new LinkedHashMap<>();
    if (state != null && state.getItems() != null) {
      for (PinboardItemDTO item : state.getItems()) {
        if (item != null && item.getId() != null) {
          byId.put(item.getId(), item);
        }
      }
    }
    return byId;
  }

  /**
   * Keyed by directed endpoint pair — the board never creates the reverse of an existing link, and
   * REMOVE_LINK matches on the exact start/end ids, so the directed key is the link's identity.
   */
  private static Map<String, PinboardLinkDTO> linksByKey(PinboardStateDTO state) {
    Map<String, PinboardLinkDTO> byKey = new LinkedHashMap<>();
    if (state != null && state.getLinks() != null) {
      for (PinboardLinkDTO link : state.getLinks()) {
        if (link != null && link.getStartItemId() != null && link.getEndItemId() != null) {
          byKey.put(link.getStartItemId() + "->" + link.getEndItemId(), link);
        }
      }
    }
    return byKey;
  }
}
