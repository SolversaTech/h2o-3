package water.test.dummy;

import hex.Model;
import water.Key;

public class DummyModelParameters extends Model.Parameters {
  public DummyAction _action;
  public boolean _makeModel;
  public boolean _cancel_job;
  public DummyModelParameters() {}
  public DummyModelParameters(String msg, Key trgt) {
    _action = new MessageInstallAction(trgt, msg);
  }
  @Override public String fullName() { return algoName(); }
  @Override public String algoName() { return "dummymodelbuilder"; }
  @Override public String javaName() { return DummyModelBuilder.class.getName(); }
  @Override public long progressUnits() { return 1; }
}
