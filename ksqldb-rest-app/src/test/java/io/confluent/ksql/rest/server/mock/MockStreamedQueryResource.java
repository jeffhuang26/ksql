/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.rest.server.mock;

import static io.confluent.ksql.GenericRow.genericRow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.ksql.json.JsonMapper;
import io.confluent.ksql.rest.entity.KsqlRequest;
import io.confluent.ksql.rest.entity.StreamedRow;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

@Path("/query")
@Produces(MediaType.APPLICATION_JSON)
public class MockStreamedQueryResource {
  private final List<TestStreamWriter> writers = new java.util.LinkedList<>();
  private long responseDelay = 0;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response streamQuery(final KsqlRequest request) throws Exception {
    Thread.sleep(responseDelay);
    final TestStreamWriter testStreamWriter = new TestStreamWriter();
    writers.add(testStreamWriter);
    return Response.ok().entity(testStreamWriter).build();
  }

  public void setResponseDelay(final long delay) {
    responseDelay = delay;
  }

  public List<TestStreamWriter> getWriters() { return writers; }

  public static class TestStreamWriter implements StreamingOutput {
    BlockingQueue<String> dataq = new LinkedBlockingQueue<>();
    ObjectMapper objectMapper = JsonMapper.INSTANCE.mapper;

    public void enq(final String data) throws InterruptedException { dataq.put(data); }

    public void finished() throws InterruptedException { dataq.put(""); }

    private void writeRow(final String data, final OutputStream out) throws IOException {
      final String toWrite = data.startsWith("{") ? data : formatData(data);
      out.write(toWrite.getBytes(StandardCharsets.UTF_8));
      out.write("\n".getBytes(StandardCharsets.UTF_8));
      out.flush();
    }

    private String formatData(final String data) throws JsonProcessingException {
      return objectMapper.writeValueAsString(StreamedRow.row(genericRow(data)));
    }

    @Override
    public void write(final OutputStream out) throws IOException, WebApplicationException {
      out.write("\n".getBytes(StandardCharsets.UTF_8));
      out.flush();
      while (true) {
        final String data;
        try {
          data = dataq.take();
        } catch (final InterruptedException e) {
          throw new RuntimeException("take interrupted");
        }
        if (data.equals("")) {
          break;
        }
        writeRow(data, out);
      }
    }
  }
}
