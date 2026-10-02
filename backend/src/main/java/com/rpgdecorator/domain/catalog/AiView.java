package com.rpgdecorator.domain.catalog;

import com.rpgdecorator.domain.Combatant;

/** What the enemy AI may look at when it decides; both combatants are OUTER references. */
public record AiView(Combatant self, Combatant opponent, int opponentBuffCount) {

    public boolean selfHealthBelow(int percent) {
        return self.currentHealth() * 100 < percent * self.stats().maxHealth();
    }
}
