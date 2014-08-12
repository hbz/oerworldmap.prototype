# to install and index: 
# create the genoames data and index that
# create (Transform) the ocwc and wsis data , partyl via API lookups
	# create OCWC description itself, copy that to "output"
	# convert these ntriples (residing in the output/ ) to Json and index that

JAR=oer-data-0.1.0-jar-with-dependencies.jar
cd ../../
mvn assembly:assembly  -DdescriptorId=jar-with-dependencies -DskipTests
cd target/ ; cp ../src/main/resources/morph-functions.properties ./ ; jar uf $JAR morph-functions.properties ; mkdir schemata; cp ../src/main/resources/schemata/* schemata/ ; jar uf $JAR schemata/* ; cd -
java -classpath classes:target/$JAR  org.hbz.oerworldmap.Geonames
java -classpath classes:target/$JAR org.hbz.oerworldmap.Transform
cd  doc/scripts/ ; bash createOcwcDescription.sh
cd ../../; java -classpath classes:target/$JAR  org.hbz.oerworldmap.NtToEs

