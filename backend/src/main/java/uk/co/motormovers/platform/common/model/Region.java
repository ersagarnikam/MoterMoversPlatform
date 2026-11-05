package uk.co.motormovers.platform.common.model;

/**
 * Data region. Each deployment stores data for exactly one region, and its country settings (currency, tax, time
 * zone, school year) follow the same choice ({@code PLATFORM_REGION}; see CountrySettings). Stored by name in
 * {@code school.region}, which has no check constraint, so a new region needs no migration.
 */
public enum Region {
    UK,
    /** Ireland (2026-11-06, go-live plan): its own deployment, with data kept in the EU. */
    IE,
    AU
}
