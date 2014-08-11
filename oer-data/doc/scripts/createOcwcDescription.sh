# create the RDF description of the OCWC itself:
PATH_MAIN_RESOURCES=../../src/main/resources

cp additionalDataOcwcItself.ntriple.template $PATH_MAIN_RESOURCES/additionalData.nt ; grep "ocwc[0-9]" $PATH_MAIN_RESOURCES/internalId2uuid.tsv | sed -e "s#.*\t\(.*\)#<http://lobid.org/oer/88feda8d-73bc-4b26-84be-f39687c31a04\#!> <http://schema.org/member> <http://lobid.org/oer/\1\#!> .#g"  >>  $PATH_MAIN_RESOURCES/additionalData.nt

# validation
rapper -i ntriples $PATH_MAIN_RESOURCES/additionalData.nt

cp  $PATH_MAIN_RESOURCES/additionalData.nt ../../output/ocwc/
