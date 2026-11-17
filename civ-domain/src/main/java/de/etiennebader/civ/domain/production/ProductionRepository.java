package de.etiennebader.civ.domain.production;

public interface ProductionRepository {

    ProductionRegistry load();

    void save(ProductionRegistry registry);
}
