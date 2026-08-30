package ui.casemaker.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/**
 * Behaviour of {@link SuspectDraft}, its state machine, and the {@link CaseDraft} id registries.
 */
public class SuspectDraftTest {

  @Test
  public void aSuspectAddedToTheCaseIsRetrievable() {
    CaseDraft draft = new CaseDraft();
    SuspectDraft valet = draft.addSuspect("The Valet");

    assertEquals(1, draft.getSuspects().size());
    assertSame(valet, draft.getSuspects().get(0));
    assertEquals("the_valet", valet.getId());
  }

  @Test
  public void homeRoomIsHeldByReferenceSoRenamesPropagate() {
    CaseDraft draft = new CaseDraft();
    RoomDraft parlour = draft.addRoom("Parlour");
    SuspectDraft valet = draft.addSuspect("Valet");
    valet.setHomeRoom(parlour);

    draft.renameRoom(parlour, "Grand Parlour");

    assertEquals("Grand Parlour", valet.getHomeRoom().getName());
  }

  @Test
  public void positionIsClampedAndScaleIgnoresNonPositive() {
    SuspectDraft suspect = new SuspectDraft("X");
    suspect.setPosition(1.5, -0.4);
    assertEquals(1.0, suspect.getPosX(), 1e-9);
    assertEquals(0.0, suspect.getPosY(), 1e-9);

    suspect.setImageScale(2.0);
    suspect.setImageScale(-1.0); // ignored
    assertEquals(2.0, suspect.getImageScale(), 1e-9);
  }

  @Test
  public void stateAccessorCreatesOnceAndReturnsTheSameInstance() {
    SuspectDraft suspect = new SuspectDraft("X");
    SuspectStateDraft lie = suspect.state("LIE");
    lie.setStatement("I was home.");

    assertSame(lie, suspect.state("lie")); // case-insensitive, same instance
    assertEquals("I was home.", suspect.state("LIE").getStatement());
  }

  @Test
  public void contradictionRewardMintsADeductionVisibleInTheRegistry() {
    CaseDraft draft = new CaseDraft();
    SuspectDraft valet = draft.addSuspect("Valet");
    ContradictionDraft rule = valet.state("LIE").addContradiction();
    rule.setRewardDeductionId("valet_opportunity");

    assertTrue(draft.deductionIds().contains("valet_opportunity"));
  }

  @Test
  public void evidenceChoicesUnionObjectAndDeductionIds() {
    CaseDraft draft = new CaseDraft();
    RoomDraft hall = draft.addRoom("Hall");
    hall.addObject("Torn Letter"); // id "torn_letter"
    SuspectDraft valet = draft.addSuspect("Valet");
    valet.state("LIE").addContradiction().setRewardDeductionId("valet_lied");

    List<String> choices = draft.evidenceChoices();

    assertTrue(choices.contains("torn_letter"));
    assertTrue(choices.contains("valet_lied"));
  }
}
