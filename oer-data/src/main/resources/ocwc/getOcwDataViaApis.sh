#!/bin/bash
# get ocwc data from the different sources
# 1.: get all id's and basic info:
wget 'http://members.ocwconsortium.org/api/v1/organization/group_by/membership_type/list/?format=json' -O tmp.json ; cat tmp.json | json_pp > ocwConsortiumMembers_Pretty.json
#2. : make id list
grep '"id" :' ocwConsortiumMembers_Pretty.json |sed  's#.*\"id\"\ :\ \([0-9]*\).*#\1#g' > ocwIds.txt
# 3. get organization detailed infos via id
for id in $(cat ocwIds.txt); do
  wget "http://members.ocwconsortium.org/api/v1/organization/view/$id/?format=json" -O organizationId/$id.json
done
#4. : get geo infos as a list:
wget "http://members.ocwconsortium.org/api/v1/address/list/geo/?format=json" -O ocwGeoList.json
