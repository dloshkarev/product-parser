# Product catalog parser tools

### Selenium setup

https://tecadmin.net/setup-selenium-chromedriver-on-ubuntu/

### Run selenium standalone server

xvfb-run java -Dwebdriver.chrome.driver=/usr/bin/chromedriver -jar selenium-server-standalone-3.13.0.jar

### Run app

- build 

sbt clean assembly

- run

scala -classpath target/scala-2.11/product-parser-assembly-0.1.jar org.epicsquad.Main
