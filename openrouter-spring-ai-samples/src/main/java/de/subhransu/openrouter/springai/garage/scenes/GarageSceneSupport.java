package de.subhransu.openrouter.springai.garage.scenes;

import de.subhransu.openrouter.springai.garage.evidence.GarageFeature;
import java.util.List;

/** Shared immutable scene metadata and feature lookup. */
abstract class GarageSceneSupport implements GarageScene {

  private final String id;
  private final String title;
  private final String description;
  private final boolean offline;

  GarageSceneSupport(String id, String title, String description, boolean offline) {
    this.id = id;
    this.title = title;
    this.description = description;
    this.offline = offline;
  }

  @Override
  public final String id() {
    return this.id;
  }

  @Override
  public final String title() {
    return this.title;
  }

  @Override
  public final String description() {
    return this.description;
  }

  @Override
  public final List<GarageFeature> features() {
    return GarageFeature.forScene(this.id);
  }

  @Override
  public final boolean offline() {
    return this.offline;
  }
}
