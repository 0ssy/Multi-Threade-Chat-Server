@echo off
mvn -q install
start java -cp server/target/server-1.0.0-shaded.jar com.ChatPlatform.server.ServerMain 5000