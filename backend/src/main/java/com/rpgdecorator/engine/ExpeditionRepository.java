package com.rpgdecorator.engine;

import com.rpgdecorator.engine.expedition.Expedition;

import java.util.Optional;

/**
 * Storage of the expeditions (a port of the engine). The in-memory implementation belongs to
 * infrastructure (T-306, RNF-06). Implementations must be thread-safe: different expeditions are
 * used concurrently (each one is only mutated under its own lock, see {@code ExpeditionService}).
 */
public interface ExpeditionRepository {

    /** Stores the expedition under {@link Expedition#id()}, replacing any previous one. */
    void save(Expedition expedition);

    /** @return the expedition, or empty if there is none with that id (or the id is {@code null}) */
    Optional<Expedition> findById(String id);

    /** @return {@code true} if an expedition with that id existed and was removed */
    boolean delete(String id);
}
