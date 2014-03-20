#!/bin/bash
# If you have new data the generated uuid is missing in the
# concordance "internalId2uuid.tsv" . This script rebuilds the
# concordance and adds new ids. Be aware to have only _one_
# dct:identifier per internal ID in the ntriples, otherwise
# this scripts produces multiple concordances for one ID

CONCORDANCE_FILE="../../src/main/resources/internalId2uuid.tsv"

grep '1.1/identifier' ../../src/main/resources/ocwc.nt | sed -e 's#.*resource/\(.*\)> <http://purl.org/dc/elements/1.1/identifier> "\(.*\)".*#\2\t\1#'  >  $CONCORDANCE_FILE

for i in $(ls ../../tmp/geo/ocwc/geoList/o/ocwc*); do
  uuid=$(grep "schema.org/address>" $i | cut -d ' ' -f3 | sed -e 's#.*:.*:\(.*\)>#\t\1#')
  echo "$(basename $i | cut -d '.' -f 1)Address$uuid" >> $CONCORDANCE_FILE
done
