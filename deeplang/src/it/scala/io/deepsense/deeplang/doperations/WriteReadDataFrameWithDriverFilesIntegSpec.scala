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

package io.deepsense.deeplang.doperations

import java.sql.Timestamp

import org.apache.spark.sql.Row
import org.apache.spark.sql.types._
import org.scalatest.BeforeAndAfter

import io.deepsense.deeplang.doperations.readwritedataframe.FileScheme
import io.deepsense.deeplang.{TestFiles, DeeplangIntegTestSupport}
import io.deepsense.deeplang.doperables.dataframe.DataFrame
import io.deepsense.deeplang.doperations.inout._

class WriteReadDataFrameWithDriverFilesIntegSpec
  extends DeeplangIntegTestSupport
  with BeforeAndAfter with TestFiles {

  import DeeplangIntegTestSupport._

  val timestamp = new Timestamp(System.currentTimeMillis())

  val schema: StructType =
    StructType(Seq(
      StructField("boolean", BooleanType),
      StructField("double", DoubleType),
      StructField("string", StringType),
      StructField("timestamp", TimestampType)
    ))

  val rows = {
    val base = Seq(
      Row(true, 0.45, "3.14", timestamp),
      Row(false, null, "\"testing...\"", null),
      Row(false, 3.14159, "Hello, world!", timestamp),
      // in case of CSV, an empty string is the same as null - no way around it
      Row(null, null, "", null)
    )
    val repeatedFewTimes = (1 to 10).flatMap(_ => base)
    repeatedFewTimes
  }

  lazy val dataFrame = createDataFrame(rows, schema)

  "WriteDataFrame and ReadDataFrame" should {
    "write and read CSV file" in {
      val wdf =
        new WriteDataFrame()
          .setStorageType(
            OutputStorageTypeChoice.File()
              .setOutputFile(absoluteTestsDirPath.fullPath + "/test_files")
              .setFileFormat(
                OutputFileFormatChoice.Csv()
                  .setCsvColumnSeparator(CsvParameters.ColumnSeparatorChoice.Tab())
                  .setCsvNamesIncluded(true)))
      wdf.execute(executionContext)(Vector(dataFrame))

      val rdf =
        new ReadDataFrame()
          .setStorageType(
            InputStorageTypeChoice.File()
              .setSourceFile(absoluteTestsDirPath.fullPath + "/test_files")
              .setFileFormat(InputFileFormatChoice.Csv()
                .setCsvColumnSeparator(CsvParameters.ColumnSeparatorChoice.Tab())
                .setCsvNamesIncluded(true)
                .setShouldConvertToBoolean(true)))
      val loadedDataFrame = rdf.execute(executionContext)(Vector()).head.asInstanceOf[DataFrame]

      assertDataFramesEqual(loadedDataFrame, dataFrame, checkRowOrder = false)
    }

    "write and read JSON file" in {
      val convertedSchema = StructType(schema.map {
        case field@StructField("timestamp", _, _, _) =>
          field.copy(dataType = StringType)
        case field => field
      })

      val timestampFieldIndex = convertedSchema.fieldIndex("timestamp")

      // JSON format does not completely preserve schema.
      // Timestamp columns are converted to string columns by WriteDataFrame operation.
      val convertedRows = rows.map {
        row =>
          val timestampValue = row.get(timestampFieldIndex)
          if (timestampValue != null) {
            Row.fromSeq(row.toSeq.updated(timestampFieldIndex, timestampValue.toString))
          } else {
            row
          }
      }
      val convertedDataFrame = createDataFrame(convertedRows, convertedSchema)

      val wdf =
        new WriteDataFrame()
          .setStorageType(OutputStorageTypeChoice.File()
            .setOutputFile(absoluteTestsDirPath.fullPath + "json")
            .setFileFormat(OutputFileFormatChoice.Json()))

      wdf.execute(executionContext)(Vector(dataFrame))

      val rdf =
        new ReadDataFrame()
          .setStorageType(InputStorageTypeChoice.File()
            .setSourceFile(absoluteTestsDirPath.fullPath + "json")
            .setFileFormat(InputFileFormatChoice.Json()))
      val loadedDataFrame = rdf.execute(executionContext)(Vector()).head.asInstanceOf[DataFrame]

      assertDataFramesEqual(loadedDataFrame, convertedDataFrame, checkRowOrder = false)
    }
  }
}
