from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.isolationforest import H2OIsolationforestEstimator


def isolation_forest():
    print("Isolation Forest Smoke Test")

    train = h2o.import_file(pyunit_utils.locate("smalldata/anomaly/ecg_discord_train.csv"))
    test = h2o.import_file(pyunit_utils.locate("smalldata/anomaly/ecg_discord_test.csv"))

    if_model = H2OIsolationforestEstimator(ntrees=7)
    if_model.train(training_frame=train)

    test_pred = if_model.predict(test)

    assert test_pred.nrow == 23

if __name__ == "__main__":
    pyunit_utils.standalone_test(isolation_forest)
else:
    isolation_forest()