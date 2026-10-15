package co.surumene.wgl.api;

@FunctionalInterface
public interface MarkerScheme {
    MarkerResult marker(BackboneDefinition backbone, DiploidGenome genome);
}
