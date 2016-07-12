package com.databricks.apps.logs;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import org.apache.commons.io.IOUtils;
import scala.Tuple2;
import scala.Tuple4;

import java.io.*;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Renderer implements Serializable {
  private String fileTemplate;
  private String pieTemplate;

  public void render(LogStatistics allOfTime, LogStatistics lastWindow)
      throws Exception {
    if (fileTemplate == null) {
      InputStream inputStream = Renderer.class.getClassLoader().getResourceAsStream("index.html.template");
      fileTemplate = IOUtils.toString(inputStream, Charsets.UTF_8);
    }
    if (pieTemplate == null){
      pieTemplate = IOUtils.toString(Renderer.class.getClassLoader().getResourceAsStream("script.js.template"));
    }

    Gson gson = new Gson();

    // TODO: Replace this hacky String replace with a proper HTML templating library.
    String output = fileTemplate;
    output = output.replace("${logLinesTable}", logLinesTable(allOfTime, lastWindow));
    output = output.replace("${contentSizesTable}", contentSizesTable(allOfTime, lastWindow));
    output = output.replace("${responseCodeTable}", responseCodeTable(allOfTime, lastWindow));
    output = output.replace("${topEndpointsTable}", topEndpointsTable(allOfTime, lastWindow));
    output = output.replace("${frequentIpAddressTable}", frequentIpAddressTable(allOfTime, lastWindow));


    try(Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
            Flags.getInstance().getOutputHtmlFile())))){
      out.write(output);
    }

    String outputPie = pieTemplate;
    List<PieClass> toPie =  allOfTime.getResponseCodeToCount().entrySet()
            .stream()
            .map(a -> new PieClass(a.getKey().toString(), a.getValue()))
            .collect(Collectors.toList());

    List<PieClass> toPie2 =  allOfTime.getIpAddresses().stream()
            .limit(15)
            .map(a -> new PieClass(a._1(), a._2()))
            .collect(Collectors.toList());

    outputPie = outputPie.replace("${IDPIE}", "piecodes");
    outputPie = outputPie.replace("${with}", "590");

    outputPie = outputPie.replace("${CONTENT}",  "\"content\":" + gson.toJson(toPie) );

    String outputpie2 = pieTemplate;
    outputpie2 = outputpie2.replace("${IDPIE}", "pie2");
    outputpie2 = outputpie2.replace("${with}", "890");
    outputpie2 = outputpie2.replace("${CONTENT}",  "\"content\":" + gson.toJson(toPie2) );

    try(Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("output/script.js")))){
      out.write(outputPie + "\n" + outputpie2);
    }

    DecimalFormat df = new DecimalFormat("#.#####");
    double total = allOfTime.getTopEndpoints().stream()
            .mapToDouble(a -> a._2())
            .sum();
    try(Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("output/data.tsv")))){
      out.write("letter\tfrequency\n");
      out.write(IntStream.rangeClosed(1, allOfTime.getTopEndpoints().size())
              .mapToObj(a -> a + "\t" + df.format(allOfTime.getTopEndpoints().get(a - 1)._2() / total))
              .collect(Collectors.joining("\n")));

    }

  }

  public String logLinesTable(LogStatistics allOfTime, LogStatistics lastWindow) {
    return "<table class=\"table table-striped\">" +
        String.format("<tr><th>All Of Time:</th><td>%s</td></tr>",
            allOfTime.getContentSizeStats()._1()) +
        String.format("<tr><th>Last Time Window:</th><td>%s</td></tr>",
            lastWindow.getContentSizeStats()._1()) +
        "</table>";
  }

  public String contentSizesTable(LogStatistics allOfTime, LogStatistics lastWindow) {
    StringBuilder builder = new StringBuilder();
    builder.append("<table class=\"table table-striped\">");
    builder.append("<tr><th></th><th>All of Time</th><th> Last Time Window</th></tr>");
    Tuple4<Long, Long, Long, Long> totalStats = allOfTime.getContentSizeStats();
    Tuple4<Long, Long, Long, Long> lastStats = lastWindow.getContentSizeStats();
    builder.append(String.format("<tr><th>Avg:</th><td>%s</td><td>%s</td>",
        totalStats._1() > 0 ? totalStats._2() / totalStats._1() : "-",
        lastStats._1() > 0 ? lastStats._2() / lastStats._1() : "-"));
    builder.append(String.format("<tr><th>Min:</th><td>%s</td><td>%s</td>",
        totalStats._1() > 0 ? totalStats._3() : "-",
        lastStats._1() > 0 ? lastStats._3() : "-"));
    builder.append(String.format("<tr><th>Max:</th><td>%s</td><td>%s</td>",
        totalStats._1() > 0 ? totalStats._4() : "-",
        lastStats._1() > 0 ? lastStats._4() : "-"));
    builder.append("</table>");
    return builder.toString();
  }

  public String responseCodeTable(LogStatistics allOfTime, LogStatistics lastWindow) {
    StringBuilder buffer = new StringBuilder();
    buffer.append("<table class=\"table table-striped table-hover\">");
    buffer.append("<tr><th>Response Code</th><th>All of Time</th><th> Last Time Window</th></tr>");
    Map<Integer, Long> lastWindowMap = lastWindow.getResponseCodeToCount();
    for(Map.Entry<Integer, Long> entry: allOfTime.getResponseCodeToCount().entrySet()) {
      buffer.append(String.format("<tr><td>%s</td><td>%s</td><td>%s</td>",
        entry.getKey(), entry.getValue(), lastWindowMap.get(entry.getKey()) != null ? lastWindowMap.get(entry.getKey()) :  "-" ));
    }
    buffer.append("</table>");
    return buffer.toString();
  }

  public String frequentIpAddressTable(
      LogStatistics allOfTime, LogStatistics lastWindow) {
    StringBuilder builder = new StringBuilder();
    builder.append("<table class=\"table table-striped\">");
    builder.append("<tr><th>All of Time</th><th> Last Time Window</th></tr>");
    List<Tuple2<String, Long>> totalIpAddresses = allOfTime.getIpAddresses();
    List<Tuple2<String, Long>> windowIpAddresses = lastWindow.getIpAddresses();
    for (int i = 0; i < totalIpAddresses.size(); i++) {
      builder.append(String.format("<tr><td>%s</td><td>%s</td></tr>",
          totalIpAddresses.get(i)._1(),
          i < windowIpAddresses.size() ? windowIpAddresses.get(i)._1() : "-"));
    }
    builder.append("</table>");
    return builder.toString();
  }

  public String topEndpointsTable(
      LogStatistics allOfTime, LogStatistics lastWindow) {
    StringBuilder builder = new StringBuilder();
    builder.append("<table class=\"table table-striped\">");
    builder.append("<tr><th>#</th><th>All of Time</th><th>Last Time Window</th></tr>");
    List<Tuple2<String, Long>> totalTopEndpoints = allOfTime.getTopEndpoints();
    List<Tuple2<String, Long>> windowTopEndpoints = lastWindow.getTopEndpoints();
    for (int i = 0; i < totalTopEndpoints.size(); i++) {
      builder.append(String.format("<tr><td>%s</td> <td>%s</td><td>%s</td></tr>",
           i + 1,
          totalTopEndpoints.get(i)._1(),
          i < windowTopEndpoints.size() ? windowTopEndpoints.get(i)._1() : "-"));
    }
    builder.append("</table>");
    return builder.toString();
  }
}
