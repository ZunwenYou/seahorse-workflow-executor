/**
 * Copyright 2015, deepsense.io
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

package io.deepsense.deeplang.doperables

import scala.reflect.runtime.universe._

import org.apache.spark.ml
import org.apache.spark.sql.types.StructType

import io.deepsense.deeplang.doperables.dataframe.{DataFrameColumnsGetter, DataFrame}
import io.deepsense.deeplang.params.wrappers.spark.ParamsWithSparkWrappers
import io.deepsense.deeplang.{DKnowledge, ExecutionContext, TypeUtils}

/**
 * Wrapper for creating deeplang Evaluators from spark ml Evaluators.
 * It is parametrized by evaluator type.
 *
 * @tparam E Type of wrapped ml.evaluation.Evaluator
 */
abstract class SparkEvaluatorWrapper[E <: ml.evaluation.Evaluator]
    (implicit val evaluatorTag: TypeTag[E])
  extends Evaluator
  with ParamsWithSparkWrappers {

  val sparkEvaluator: E = createEvaluatorInstance()

  def getMetricName: String

  override def _evaluate(context: ExecutionContext, dataFrame: DataFrame): MetricValue = {
    val sparkParams = sparkParamMap(sparkEvaluator, dataFrame.sparkDataFrame.schema)
    val value = sparkEvaluator.evaluate(dataFrame.sparkDataFrame, sparkParams)
    MetricValue(getMetricName, value)
  }

  override def _infer(k: DKnowledge[DataFrame]): MetricValue = {
    k.single.schema.foreach(sparkParamMap(sparkEvaluator, _))
    MetricValue.forInference(getMetricName)
  }

  def createEvaluatorInstance(): E = TypeUtils.instanceOfType(evaluatorTag)

  override def isLargerBetter: Boolean = sparkEvaluator.isLargerBetter
}
