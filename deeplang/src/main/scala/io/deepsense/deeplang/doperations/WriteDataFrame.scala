/**
 * Copyright 2015, CodiLime Inc.
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

package io.deepsense.deeplang.doperations

import java.io.{IOException, StringWriter}
import java.sql.Timestamp

import scala.collection.immutable.ListMap
import scala.reflect.runtime.{universe => ru}

import au.com.bytecode.opencsv.CSVWriter
import org.apache.commons.lang3.StringEscapeUtils
import org.apache.spark.sql.Row

import io.deepsense.commons.datetime.DateTimeConverter
import io.deepsense.deeplang.DOperation.Id
import io.deepsense.deeplang.doperables.dataframe.DataFrame
import io.deepsense.deeplang.doperables.dataframe.types.categorical.CategoricalMetadata
import io.deepsense.deeplang.doperations.WriteDataFrame._
import io.deepsense.deeplang.doperations.exceptions.DeepSenseIOException
import io.deepsense.deeplang.parameters._
import io.deepsense.deeplang.{DOperation1To0, ExecutionContext}

case class WriteDataFrame() extends DOperation1To0[DataFrame] {

  override val id: Id = "9e460036-95cc-42c5-ba64-5bc767a40e4e"

  override val name: String = "Write DataFrame"

  val columnSeparatorParameter = StringParameter(
    description = "String separating fields in a row",
    default = Some(","),
    required = true,
    validator = RegexValidator(".".r)
  )

  val writeHeaderParameter = BooleanParameter(
    description = "Should the output file include a header with column names?",
    default = Some(true),
    required = true
  )

  val formatParameter = ChoiceParameter(
    description = "Format of the output file",
    default = Some(CSV.name),
    required = true,
    options = ListMap(CSV.name -> ParametersSchema(
      "column separator" -> columnSeparatorParameter,
      "write header" -> writeHeaderParameter
    ))
  )

  val outputFileParameter = StringParameter(
    description = "Output file path",
    default = None,
    required = true,
    validator = new AcceptAllRegexValidator())

  override val parameters = ParametersSchema(
    "format" -> formatParameter,
    "output file" -> outputFileParameter
  )

  override protected def _execute(context: ExecutionContext)(dataFrame: DataFrame): Unit = {
    require(contains(formatParameter.value, CSV.toString))

    val categoricalMetadata = CategoricalMetadata(dataFrame.sparkDataFrame)
    val csv = dataFrame.sparkDataFrame.rdd
      .map(rowToStringArray(categoricalMetadata) andThen convertToCsv)

    val result = if (writeHeaderParameter.value.get) {
      context.sparkContext.parallelize(Seq(
        convertToCsv(dataFrame.sparkDataFrame.schema.fieldNames)
      )).union(csv)
    } else {
      csv
    }

    try {
      result.saveAsTextFile(outputFileParameter.value.get)
    } catch {
      case e: IOException => throw DeepSenseIOException(e)
    }
  }

  private def contains(o: Option[String], value: String): Boolean = {
    o match {
      case None => false
      case Some(x) => x == value
    }
  }

  private def rowToStringArray(categoricalMetadata: CategoricalMetadata) = (row: Row) => {
    val zippedWithIndex = row.toSeq.zipWithIndex

    val withCategoricals = zippedWithIndex.map { case (value, index) =>
      if (value != null && categoricalMetadata.isCategorical(index)) {
        categoricalMetadata.mapping(index).idToValue(value.asInstanceOf[Int])
      } else {
        value
      }
    }

    withCategoricals.toArray.map {
      case null => ""
      case true => "1"
      case false => "0"
      case s: String => StringEscapeUtils.escapeCsv(s)
      case t: Timestamp => DateTimeConverter.toString(DateTimeConverter.fromMillis(t.getTime))
      case x => x.toString
    }
  }

  val convertToCsv = (data: Array[String]) => {
    import scala.collection.JavaConversions._
    val buf = new StringWriter
    val writer = new CSVWriter(buf, columnSeparatorParameter.value.get.charAt(0),
      CSVWriter.NO_QUOTE_CHARACTER, CSVWriter.NO_ESCAPE_CHARACTER)
    writer.writeAll(List(data))
    buf.toString.trim
  }

  @transient
  override lazy val tTagTI_0: ru.TypeTag[DataFrame] = ru.typeTag[DataFrame]
}

object WriteDataFrame {
  sealed abstract class FileType(val name: String)

  object FileType {
    def forName(n: String): FileType = n.toLowerCase match {
      case "csv" => CSV
      case _ => ???
    }
  }

  case object CSV extends FileType("CSV")

  def apply(
      fileType: FileType,
      columnSeparator: String,
      writeHeader: Boolean,
      path: String): WriteDataFrame = {

    val writeDataFrame = WriteDataFrame()
    writeDataFrame.formatParameter.value = Some(fileType.name)
    writeDataFrame.columnSeparatorParameter.value = Some(columnSeparator)
    writeDataFrame.writeHeaderParameter.value = Some(writeHeader)
    writeDataFrame.outputFileParameter.value = Some(path)
    writeDataFrame
  }
}
