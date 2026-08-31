package Core;

public class Letter {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Letter.class);

  private String invitation;
  private String caseDescription;

  public void setInvitation(String invitation) {
    this.invitation = invitation;
  }

  public void setCaseDescription(String caseDescription) {
    this.caseDescription = caseDescription;
  }

  public void displayInvitation() {
    logger.debug(invitation);
  }

  public void displayCaseDescription() {
    logger.debug(caseDescription);
  }
}
