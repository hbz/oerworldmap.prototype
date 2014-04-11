#!/bin/bash
# If you have new data the generated uuid is missing in the
# concordance "internalId2uuid.tsv" . This script rebuilds the
# concordance and adds new ids. Be aware to have only _one_
# dct:identifier per internal ID in the ntriples, otherwise
# this scripts produces multiple concordances for one ID.
#
# There are no more bnodes anymore. These will be substituted
# with uuids. See 1., 2., 3. for all generated uuids.


CONCORDANCE_FILE_TMP="../../src/main/resources/internalId2uuid.tmp"
CONCORDANCE_FILE="../../src/main/resources/internalId2uuid.tsv"

# 1. internalWsisAddressId -> uuid
rm $CONCORDANCE_FILE_TMP
for i in $(ls ../../tmp/wsis/wsis-initiative-data.json/w/*); do
  uuid=$(grep "schema.org/address>" $i | cut -d ' ' -f3 | sed -e 's#.*:.*:\(.*\)>#\t\1#')
  echo "$(basename $i | cut -d '.' -f 1)Address$uuid" >> $CONCORDANCE_FILE_TMP
done
exit
sort -u $CONCORDANCE_FILE_TMP > $CONCORDANCE_FILE
exit

# 1. internalWsisId -> uuid
grep '1.1/identifier' ../../tmp/oerWorldmapTestResult.nt | sed -e 's#.*oer/\(.*\)\#!> <http://purl.org/dc/elements/1.1/identifier> "\(.*\)".*#\2\t\1#'  >  $CONCORDANCE_FILE_TMP
sort -u $CONCORDANCE_FILE_TMP >> $CONCORDANCE_FILE
exit
# 1. internalOcwcId -> uuid
grep '1.1/identifier' ocw*.nt | sed -e 's#.*oer/\(.*\)\#!> <http://purl.org/dc/elements/1.1/identifier> "\(.*\)".*#\2\t\1#'  >>  $CONCORDANCE_FILE_TMP

# 2. internalOcwcIdAddress -> uuid
for i in $(ls ../../tmp/geo/ocwc/geoList/o/ocwc*); do
  uuid=$(grep "schema.org/address>" $i | cut -d ' ' -f3 | sed -e 's#.*:.*:\(.*\)>#\t\1#')
  echo "$(basename $i | cut -d '.' -f 1)Address$uuid" >> $CONCORDANCE_FILE_TMP
done

# 3. serviceUrls -> uuid
for i in $(ls ../../tmp/ocwc/organizationId/o/ocwc*); do
  for schemaUrl in $(grep "schema.org/url>"  $i | cut -d ' ' -f3 | sed -e 's#<\(.*\)>#\1#g'); do
    for uuid in $(grep "schema.org/url>"  $i | cut -d ' ' -f1 | sed -e 's#<http://lobid\.org/oer/\(.*\)\#!>#\1#g'); do
      grep "$uuid.*schema.org/Service" $i
      if [ $? -eq 0 ]; then
        grep "$uuid.*$schemaUrl" $i
        if [ $? -eq 0 ]; then
         echo -e "$schemaUrl\t$uuid" >> $CONCORDANCE_FILE_TMP
        fi
      fi
    done
  done
done
# sort to diff easier
sort -u $CONCORDANCE_FILE_TMP > $CONCORDANCE_FILE
