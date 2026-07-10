java \
  -XX:StartFlightRecording=duration=20s,filename=json_profile.jfr \
  -XX:-TieredCompilation \
  -cp build/modulepath/cascara-common-1.1.7.jar:build/modulepath/cascara-lang-json-0.5.2.jar \
  io.github.qishr.cascara.lang.json.util.ProfilingHarness

