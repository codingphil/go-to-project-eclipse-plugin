package dev.lonsing.eclipse.plugins.gotoproject.exceptions;

public class GoToProjectException extends RuntimeException {

  private static final long serialVersionUID = 8704284237000835707L;

  public GoToProjectException(Throwable t) {
    super(t);
  }

  public GoToProjectException(String message, Throwable t) {
    super(message, t);
  }

}
