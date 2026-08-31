package Core;

import java.util.List;

public class TaskList {
  private List<String> tasks;

  public TaskList(List<String> tasks) {
    this.tasks = tasks;
  }

  // Provide data access instead of IO
  public List<String> getTasks() {
    return tasks;
  }

  public boolean isEmpty() {
    return tasks.isEmpty();
  }
}
