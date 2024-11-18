#!/usr/bin/env python3
# Define a comprehensive dictionary for benchmarks
all_benchmarks = {
    # 1
    "antlr": {
        "main_class": "dacapo.antlr.Main",
        "app_path": "benchmarks/dacapo2006/antlr.jar",
        "lib_path": "benchmarks/dacapo2006/antlr-deps.jar",
        "tamiflex_log": "benchmarks/dacapo2006/antlr-refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "dacapo2006",
    },
    # 2
    "bloat": {
        "main_class": "dacapo.bloat.Main",
        "app_path": "benchmarks/dacapo2006/bloat.jar",
        "lib_path": "benchmarks/dacapo2006/bloat-deps.jar",
        "tamiflex_log": "benchmarks/dacapo2006/bloat-refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "dacapo2006",
    },
    # 3
    "chart": {
        "main_class": "dacapo.chart.Main",
        "app_path": "benchmarks/dacapo2006/chart.jar",
        "lib_path": "benchmarks/dacapo2006/chart-deps.jar",
        "tamiflex_log": "benchmarks/dacapo2006/chart-refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "dacapo2006",
    },
    # 4
    "eclipse": {
        "main_class": "dacapo.eclipse.Main",
        "app_path": "benchmarks/dacapo2006/eclipse.jar",
        "lib_path": "benchmarks/dacapo2006/eclipse-deps.jar",
        "tamiflex_log": "benchmarks/dacapo2006/eclipse-refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "dacapo2006",
    },
    # 5
    "fop": {
        "main_class": "dacapo.fop.Main",
        "app_path": "benchmarks/dacapo2006/fop.jar",
        "lib_path": "benchmarks/dacapo2006/fop-deps.jar",
        "tamiflex_log": "benchmarks/dacapo2006/fop-refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "dacapo2006",
    },
    # 6
    "luindex": {
        "main_class": "dacapo.luindex.Main",
        "app_path": "benchmarks/dacapo2006/luindex.jar",
        "lib_path": "benchmarks/dacapo2006/luindex-deps.jar",
        "tamiflex_log": "benchmarks/dacapo2006/luindex-refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "dacapo2006",
    },
    # 7
    "lusearch": {
        "main_class": "dacapo.lusearch.Main",
        "app_path": "benchmarks/dacapo2006/lusearch.jar",
        "lib_path": "benchmarks/dacapo2006/lusearch-deps.jar",
        "tamiflex_log": "benchmarks/dacapo2006/lusearch-refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "dacapo2006",
    },
    # 8
    "pmd": {
        "main_class": "dacapo.pmd.Main",
        "app_path": "benchmarks/dacapo2006/pmd.jar",
        "lib_path": "benchmarks/dacapo2006/pmd-deps.jar",
        "tamiflex_log": "benchmarks/dacapo2006/pmd-refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "dacapo2006",
    },
    # 9
    "xalan": {
        "main_class": "dacapo.xalan.Main",
        "app_path": "benchmarks/dacapo2006/xalan.jar",
        "lib_path": "benchmarks/dacapo2006/xalan-deps.jar",
        "tamiflex_log": "benchmarks/dacapo2006/xalan-refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "dacapo2006",
    },
    # 10
    "checkstyle": {
        "main_class": "com.puppycrawl.tools.checkstyle.Main",
        "app_path": "benchmarks/applications/checkstyle/checkstyle-5.7-all.jar",
        "lib_path": "benchmarks/applications/checkstyle/",
        "tamiflex_log": "benchmarks/applications/checkstyle/checkstyle-refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "non-dacapo",
    },
    # 11
    "findbugs": {
        "main_class": "edu.umd.cs.findbugs.FindBugs",
        "app_path": "benchmarks/applications/findbugs/findbugs.jar",
        "lib_path": "benchmarks/applications/findbugs/",
        "tamiflex_log": "benchmarks/applications/findbugs/findbugs-refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "non-dacapo",
    },
    # 12
    "JPC": {
        "main_class": "org.jpc.j2se.JPCApplication",
        "app_path": "benchmarks/applications/JPC/JPCApplication.jar",
        "lib_path": "benchmarks/applications/JPC/",
        "tamiflex_log": "benchmarks/applications/JPC/JPC-refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "non-dacapo",
    },
    # 13
    "hsqldb": {
        "main_class": "dacapo.hsqldb.Main",
        "app_path": "benchmarks/dacapo2006/hsqldb.jar",
        "lib_path": "benchmarks/dacapo2006/hsqldb-deps.jar",
        "tamiflex_log": "benchmarks/dacapo2006/hsqldb-refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "dacapo2006",
    },
    # 14 dacapo-23.11
    "biojava": {
        "main_class": "org.biojava.nbio.aaproperties.CommandPrompt",
        "app_path": "benchmarks/dacapo-23.11-chopin/jar/biojava/AAProperties-jar-with-dependencies.jar",
        "lib_path": "benchmarks/dacapo-23.11-chopin/jar/biojava/",
        "tamiflex_log": "",
        "jre_version": "jre1.6.0_45",
        "source": "dacapo-23.11",
    },
    # 15
    "avrora": {
        "main_class": "Harness",
        "app_path": "benchmarks/dacapo-bach/avrora.jar",
        "lib_path": "benchmarks/dacapo-bach/avrora-deps.jar",
        "tamiflex_log": "benchmarks/dacapo-bach/avrora-tamiflex-default.log",  # NOTE: default
        "jre_version": "jre1.6.0_45",
        "source": "dacapo-9.12",
    },
    # 16
    "sunflow": {
        "main_class": "Harness",
        "app_path": "benchmarks/dacapo-bach/sunflow.jar",
        "lib_path": "benchmarks/dacapo-bach/sunflow-deps.jar",
        "tamiflex_log": "benchmarks/dacapo-bach/sunflow-tamiflex-default.log",
        "jre_version": "jre1.6.0_45",
        "source": "dacapo-9.12",
    },
    # 17
    "tradebeans": {
        "main_class": "Harness",
        "app_path": "benchmarks/dacapo-bach/tradebeans.jar",
        "lib_path": "benchmarks/dacapo-bach/tradebeans-deps.jar",
        "tamiflex_log": "benchmarks/dacapo-bach/tradebeans-tamiflex.log",
        "jre_version": "jre1.6.0_45",
        "source": "dacapo-9.12",
    },
    # 18
    "batik": {
        "main_class": "Harness",
        "app_path": "benchmarks/dacapo-bach/batik.jar",
        "lib_path": "benchmarks/dacapo-bach/batik-deps.jar",
        "tamiflex_log": "benchmarks/dacapo-bach/batik-tamiflex-default.log",
        "jre_version": "jre1.6.0_45",
        "source": "dacapo-9.12",
    },
    # 19
    "h2": {
        "main_class": "Harness",
        "app_path": "benchmarks/dacapo-bach/h2.jar",
        "lib_path": "benchmarks/dacapo-bach/h2-deps.jar",
        "tamiflex_log": "benchmarks/dacapo-bach/h2-tamiflex-default.log",
        "jre_version": "jre1.6.0_45",
        "source": "dacapo-9.12",
    },
    # 20
    "tomcat": {
        "main_class": "org.apache.catalina.startup.Bootstrap",
        "app_path": "benchmarks/dacapo-23.11-chopin/jar/tomcat/catalina.jar",
        "lib_path": "benchmarks/dacapo-23.11-chopin/jar/tomcat/",
        "tamiflex_log": "benchmarks/dacapo-23.11-chopin/jar/tomcat/tomcat-tamiflex.log",
        "jre_version": "jre1.6.0_45",
        "source": "dacapo-23.11",
    },
    # github
    # 21
    "classyshark": {
        "main_class": "com.google.classyshark.Shark",
        "app_path": "benchmarks/github/classyshark/ClassyShark.jar",
        "lib_path": "benchmarks/github/classyshark/",
        "tamiflex_log": "benchmarks/github/classyshark/refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "github",
    },
    # 22
    "jd": {
        "main_class": "org.jd.gui.App",  # org.jd.gui.OsxApp, org.fife.ui.rsyntaxtextarea.TextEditorPane,
        "app_path": "benchmarks/github/jd/jd-gui-1.6.6.jar",
        "lib_path": "benchmarks/github/jd/",
        "tamiflex_log": "benchmarks/github/jd/refl.log",  # cannot be acquired by tamiflex
        "jre_version": "jre1.6.0_45",
        "source": "github",
    },
    # 23
    "bytecodeviewer": {
        "main_class": "org.benf.cfr.reader.Main",
        "app_path": "benchmarks/github/bytecodeviewer/Bytecode-Viewer-2.12.jar",
        "lib_path": "benchmarks/github/bytecodeviewer/",
        "tamiflex_log": "benchmarks/github/bytecodeviewer/refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "github",
    },
    # 24
    "mindustry": {
        "main_class": "net.jpountz.lz4.LZ4Factory",
        "app_path": "benchmarks/github/mindustry/Mindustry.jar",
        "lib_path": "benchmarks/github/mindustry/",
        "tamiflex_log": "benchmarks/github/mindustry/refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "github",
    },
    # 25
    "recaf": {
        "main_class": "me.coley.recaf.Recaf",
        "app_path": "benchmarks/github/recaf/recaf-2.21.14-J8-jar-with-dependencies.jar",
        "lib_path": "benchmarks/github/recaf/",
        "tamiflex_log": "benchmarks/github/recaf/refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "github",
    },
    # 26
    "tesseract": {
        "main_class": "eu.digitisation.Main",
        "app_path": "benchmarks/github/tesseract/tesseract4java-0.2.1-linux-x86_64.jar",
        "lib_path": "benchmarks/github/tesseract/",
        "tamiflex_log": "benchmarks/github/tesseract/refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "github",
    },
    # 27
    "dcevm": {
        "main_class": "com.github.dcevm.installer.Main",
        "app_path": "benchmarks/github/dcevm/DCEVM-8u181-installer-build2.jar",
        "lib_path": "benchmarks/github/dcevm/",
        "tamiflex_log": "benchmarks/github/dcevm/refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "github",
    },
    # 28
    "ddjava": {
        "main_class": "datadog.trace.bootstrap.AgentBootstrap",
        "app_path": "benchmarks/github/ddjava/dd-java-agent-1.9.0.jar",
        "lib_path": "benchmarks/github/ddjava/",
        "tamiflex_log": "benchmarks/github/ddjava/refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "github",
    },
    # 29
    "opentelemetry": {
        "main_class": "io.opentelemetry.javaagent.OpenTelemetryAgent",
        "app_path": "benchmarks/github/opentelemetry/opentelemetry-javaagent.jar",
        "lib_path": "benchmarks/github/opentelemetry/",
        "tamiflex_log": "benchmarks/github/opentelemetry/refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "github",
    },
    # 30
    "sqlite-jdbc": {
        "main_class": "org.sqlite.util.OSInfo",
        "app_path": "benchmarks/github/sqlite/sqlite-jdbc-3.41.0.0.jar",
        "lib_path": "benchmarks/github/sqlite/",
        "tamiflex_log": "benchmarks/github/sqlite/refl.log",
        "jre_version": "jre1.6.0_45",
        "source": "github",
    },
}


BENCHMARKS = [ben for ben in all_benchmarks.keys()]
BENCHMARKS = sorted(BENCHMARKS, key=lambda x: x.lower())
print(f"valid benchmarks length: {len(BENCHMARKS)}")
CATEGORY_BENCH = {}
for ben in BENCHMARKS:
    info = all_benchmarks[ben]
    src = info["source"]
    if src in CATEGORY_BENCH:
        CATEGORY_BENCH[src].append(ben)
    else:
        CATEGORY_BENCH[src] = [ben]
        
print("=====Source Statistics=====")
for k, v in CATEGORY_BENCH.items():
    print(f"={k}:\t{len(v)}")
print("=====End=====")


MAINCLASSES = {k: v["main_class"] for k, v in all_benchmarks.items()}
APPPATH = {k: v["app_path"] for k, v in all_benchmarks.items()}
LIBPATH = {k: v["lib_path"] for k, v in all_benchmarks.items()}
TAMIFLEXLOG = {k: v["tamiflex_log"] for k, v in all_benchmarks.items()}
JREVERSION = {k: v["jre_version"] for k, v in all_benchmarks.items()}
