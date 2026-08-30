package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PublicGamesListDTO implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;
  private final List<PublicGameInfoDTO> games;

  @JsonCreator
  public PublicGamesListDTO(@JsonProperty("games") List<PublicGameInfoDTO> games) {
    this.games = games != null ? new ArrayList<>(games) : new ArrayList<>();
  }

  public List<PublicGameInfoDTO> getGames() {
    return new ArrayList<>(games);
  }

  @Override
  public String toString() {
    return "PublicGamesListDTO{" + "games_count=" + games.size() + '}';
  }
}