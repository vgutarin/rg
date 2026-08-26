package vg.rg.model;

/**
 * A saved location suggested as being near the query coordinates, with its great-circle distance in
 * meters. Advisory only — never blocks creating a new location within the radius.
 */
public record ProximityMatch(LocationModel location, double distanceMeters) {
}
