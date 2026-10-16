@rem ----------------------------------------------------------------------------
@rem Licensed to the Apache Software Foundation (ASF) under one
@rem or more contributor license agreements.  See the NOTICE file
@rem distributed with this work for additional information
@rem regarding copyright ownership.  The ASF licenses this file
@rem to you under the Apache License, Version 2.0 (the
@rem "License"); you may not use this file except in compliance
@rem with the License.  You may obtain a copy of the License at
@rem
@rem    https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing,
@rem software distributed under the License is distributed on an
@rem "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@rem KIND, either express or implied.  See the License for the
@rem specific language governing permissions and limitations
@rem under the License.
@rem ----------------------------------------------------------------------------

@rem ----------------------------------------------------------------------------
@rem Maven Wrapper startup batch script.
@rem ----------------------------------------------------------------------------

@if "%DEBUG%"=="" @echo off
@setlocal enableextensions enabledelayedexpansion

set WRAPPER_JAR=%~dp0.mvn\wrapper\maven-wrapper.jar
set DISTRIBUTION_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip
set WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar

if not exist "%WRAPPER_JAR%" (
    echo Downloading Maven Wrapper...
    if exist "%SystemRoot%\System32\curl.exe" (
        curl -sSL "%WRAPPER_URL%" -o "%WRAPPER_JAR%"
    ) else (
        powershell -Command "(New-Object Net.WebClient).DownloadFile('%WRAPPER_URL%', '%WRAPPER_JAR%')"
    )
)

set MAVEN_CMD_LINE_ARGS=%*
java -Dwrapper.distribution="%DISTRIBUTION_URL%" -Dwrapper.url="%WRAPPER_URL%" -jar "%WRAPPER_JAR%" %*
