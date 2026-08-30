package common.dto;

import java.io.Serializable;

public class VisualPositionDTO implements Serializable {
  private Double x;
  private Double y;
  // Optional clockwise sprite rotation in degrees (used on the per-element "flips" map: a rotation
  // carries alongside the mirror flags). Null = no rotation. Kept here so no extra room-DTO field
  // or constructor change is needed.
  private Double rotation;

  public VisualPositionDTO() {}

  public VisualPositionDTO(Double x, Double y) {
    this.x = x;
    this.y = y;
  }

  public Double getX() {
    return x;
  }

  public void setX(Double x) {
    this.x = x;
  }

  public Double getY() {
    return y;
  }

  public void setY(Double y) {
    this.y = y;
  }

  public Double getRotation() {
    return rotation;
  }

  public void setRotation(Double rotation) {
    this.rotation = rotation;
  }
}
