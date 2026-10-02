package com.rpgdecorator.engine.combat;

import com.rpgdecorator.engine.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActionTest {

    @Test
    void typesAreTheContractValues() {
        assertEquals("ATTACK", Action.attack().type());
        assertEquals("DEFEND", Action.defend().type());
        assertEquals("ABILITY", Action.useAbility("war_cry").type());
        assertEquals("PASS", Action.pass().type());
    }

    @Test
    void onlyUseAbilityCarriesAnAbilityId() {
        assertEquals("war_cry", Action.useAbility("war_cry").abilityId());
        assertNull(Action.attack().abilityId());
        assertNull(Action.defend().abilityId());
        assertNull(Action.pass().abilityId());
        assertThrows(NullPointerException.class, () -> new Action.UseAbility(null));
    }

    @Test
    void actionsAreValues() {
        assertEquals(new Action.Attack(), Action.attack());
        assertEquals(new Action.UseAbility("ice_bolt"), Action.useAbility("ice_bolt"));
    }

    @Test
    void fromTypeBuildsEveryAction() {
        assertEquals(Action.attack(), Action.fromType("ATTACK", null));
        assertEquals(Action.defend(), Action.fromType("DEFEND", "ignored"));
        assertEquals(Action.pass(), Action.fromType("PASS", null));
        assertEquals(Action.useAbility("ice_bolt"), Action.fromType("ABILITY", "ice_bolt"));
    }

    @Test
    void fromTypeRejectsUnknownOrIncompleteRequestsWithContractCodes() {
        assertEquals(ErrorCode.INVALID_VALUE,
                assertThrows(InvalidActionException.class, () -> Action.fromType("attack", null)).errorCode());
        assertEquals(ErrorCode.REQUIRED_FIELD,
                assertThrows(InvalidActionException.class, () -> Action.fromType(null, null)).errorCode());
        assertEquals("REQUIRED_FIELD",
                assertThrows(InvalidActionException.class, () -> Action.fromType("ABILITY", " ")).code());
    }

    @Test
    void exceptionExposesCodeAndMessage() {
        InvalidActionException error =
                new InvalidActionException(ErrorCode.ABILITY_ON_COOLDOWN, "Maleficio estará disponible en 2 turnos");
        assertEquals("ABILITY_ON_COOLDOWN", error.code());
        assertEquals(ErrorCode.ABILITY_ON_COOLDOWN, error.errorCode());
        assertEquals("Maleficio estará disponible en 2 turnos", error.getMessage());
    }
}
