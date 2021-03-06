/**
 * Copyright 2016, deepsense.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.deepsense.deeplang.doperables.spark.wrappers.estimators

import org.apache.spark.ml.classification.{DecisionTreeClassificationModel => SparkDecisionTreeClassificationModel, DecisionTreeClassifier => SparkDecisionTreeClassifier}

import io.deepsense.deeplang.doperables.SparkEstimatorWrapper
import io.deepsense.deeplang.doperables.spark.wrappers.models.{DecisionTreeClassificationModel, VanillaDecisionTreeClassificationModel}
import io.deepsense.deeplang.doperables.spark.wrappers.params.DecisionTreeParams
import io.deepsense.deeplang.doperables.spark.wrappers.params.common._
import io.deepsense.deeplang.doperables.stringindexingwrapper.StringIndexingEstimatorWrapper
import io.deepsense.deeplang.params.Param

class DecisionTreeClassifier private(
    val vanillaDecisionTreeClassifier: VanillaDecisionTreeClassifier)
  extends StringIndexingEstimatorWrapper[
    SparkDecisionTreeClassificationModel,
    SparkDecisionTreeClassifier,
    VanillaDecisionTreeClassificationModel,
    DecisionTreeClassificationModel](vanillaDecisionTreeClassifier) {

  def this() = this(new VanillaDecisionTreeClassifier())
}

class VanillaDecisionTreeClassifier
  extends SparkEstimatorWrapper[
    SparkDecisionTreeClassificationModel,
    SparkDecisionTreeClassifier,
    VanillaDecisionTreeClassificationModel]
  with HasClassificationImpurityParam
  with DecisionTreeParams
  with ProbabilisticClassifierParams
  with HasLabelColumnParam {

  override val params: Array[Param[_]] = declareParams(
    maxDepth,
    maxBins,
    minInstancesPerNode,
    minInfoGain,
    maxMemoryInMB,
    cacheNodeIds,
    checkpointInterval,
    seed,
    impurity,
    labelColumn,
    featuresColumn,
    probabilityColumn,
    rawPredictionColumn,
    predictionColumn)

  override protected def estimatorName: String = classOf[DecisionTreeClassifier].getSimpleName
}
