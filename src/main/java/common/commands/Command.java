package common.commands;

import com.fasterxml.jackson.annotation.JsonIgnore;
import common.interfaces.GameActionContext;
import java.io.Serial;
import java.io.Serializable;

// The @JsonTypeInfo annotation is no longer needed here.
public interface Command extends Serializable {
  @Serial
  long serialVersionUID = 1L;

  void execute(GameActionContext context);
  @JsonIgnore
  String getDescription();

  void setPlayerId(String playerId);

  String getPlayerId();
}