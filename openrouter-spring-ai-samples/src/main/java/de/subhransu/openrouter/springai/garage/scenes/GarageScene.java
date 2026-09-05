package de.subhransu.openrouter.springai.garage.scenes;

import de.subhransu.openrouter.springai.garage.evidence.GarageFeature;
import java.util.List;

/** One selectable Garage capability story. */
public interface GarageScene {

  String id();

  String title();

  String description();

  List<GarageFeature> features();

  boolean offline();

  SceneResult execute(SceneContext context) throws Exception;
}
