package Core;

import common.dto.JournalEntryDTO;
import common.dto.JournalEntryType;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Updated to work specifically with JournalEntryDTO, or we can keep it generic if we want.
// Given the requirements to filter by JournalEntryType, it's easier if we bind it to DTO or an interface.
// For now, I'll keep it generic <E> but assume E has accessors if needed,
// OR simpler: just make Journal<T> work, but if we need filtering logic inside here, T needs to be known.
// Let's refactor Journal to use JournalEntryDTO directly since that's what we are standardizing on.
// Or we can cast if E is generic.
// Actually, looking at the previous code, it was Journal<JournalEntryDTO> in GameContextServer.

public class Journal<E extends JournalEntryDTO> implements Serializable {
  private static final long serialVersionUID = 1L;

  private List<E> entries;

  public Journal() {
    this.entries = new ArrayList<>();
  }

  public boolean addEntry(E entry) {
    if (entry == null) {
      return false;
    }
    // Check duplication by ID if present, otherwise fallback to equals
    boolean exists = entries.stream()
        .anyMatch(e -> (e.getId() != null && e.getId().equals(entry.getId())) || e.equals(entry));

    if (!exists) {
      entries.add(entry);
      return true;
    }
    return false;
  }

  public List<E> getEntries() {
    return Collections.unmodifiableList(entries);
  }

  // --- New Query Methods ---

  public List<E> getEntriesByType(JournalEntryType type) {
    return entries.stream()
        .filter(e -> e.getType() == type)
        .collect(Collectors.toList());
  }

  public List<E> getEntriesBySourceId(String sourceId) {
    if (sourceId == null)
      return Collections.emptyList();
    return entries.stream()
        .filter(e -> sourceId.equals(e.getSourceId()))
        .collect(Collectors.toList());
  }

  public E getEntryById(String id) {
    if (id == null)
      return null;
    return entries.stream()
        .filter(e -> id.equals(e.getId()))
        .findFirst()
        .orElse(null);
  }

  public Map<JournalEntryType, List<E>> getEntriesGroupedByType() {
    return entries.stream()
        .collect(Collectors.groupingBy(JournalEntryDTO::getType));
  }

  public void clearEntries() {
    entries.clear();
  }

  public boolean isEmpty() {
    return entries.isEmpty();
  }

  public int getEntryCount() {
    return entries.size();
  }
}
