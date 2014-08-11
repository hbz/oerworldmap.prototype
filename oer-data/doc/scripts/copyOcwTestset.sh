#!/bin/bash
# builds one ntriple file using all the single ntriple files
# using e.g. for building the test file

rm  ../../output/ocwc/ocwcTestResult.nt
rm ocw_neu.nt
for i in $(find ../../output/ocwc -name "*.nt"); do
  cat $i >> ocw_neu.nt.tmp
done
sort -u ocw_neu.nt.tmp > ocw_neu.nt
rm ocw_neu.nt.tmp
echo "old vs. new"
diff ../../src/test/resources/ocwc/ocwcTestResult.nt ocw_neu.nt
#diff ../../src/test/resources/ocwc/geoOsmTestResult.nt ocw_neu.nt
echo "Copy the data to the test set: $ cp ocw_neu.nt ../../src/test/resources/ocwc/[ocwcTestResult.nt,geoOsmTestResult.nt(depending on the data produced)]; rm ocw_neu.nt"
