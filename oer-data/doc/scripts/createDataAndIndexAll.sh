# to install and index: 
# create the genoames data and index that
# create (Transform) the ocwc and wsis data , partyl via API lookups
	# create OCWC description itself, copy that to "output"
	# convert these ntriples (residing in the output/ ) to Json and index that

cd ../../;  mvn assembly:assembly  -DdescriptorId=jar-with-dependencies -DskipTests; java -classpath classes:target/oer-data-0.1.0-jar-with-dependencies.jar  org.hbz.oerworldmap.Geonames; java -classpath classes:target/oer-data-0.1.0-jar-with-dependencies.jar org.hbz.oerworldmap.Transform; cd  doc/scripts/ ; bash createOcwcDescription.sh ; cd ../../; java -classpath classes:target/oer-data-0.1.0-jar-with-dependencies.jar  org.hbz.oerworldmap.NtToEs

