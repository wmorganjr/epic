set -x

curl -v -X POST localhost:3000/drafts/new \
  -d set-names=Set\ 1,Uprising,Tyrants \
  -d player-count=2 \
  -d pack-size=12 

exit

# {
  # "draft-id": "72c71b09-5dca-4440-ac3d-6a0348908954",
  # "seats": {
    # "5b312a5c-28e8-42de-a859-f4666de3e4ca": 0,
    # "e2479453-3c01-4e10-833a-6d899b600942": 1,
    # "86aa3948-c0ff-47fc-b395-599df2f15aa0": 2,
    # "c49dd8bb-8f41-4578-8c1c-d721204ab42d": 3,
    # "a03c5920-2a9c-4b01-baf2-4692f3e8e2a7": 4,
    # "5a6a8970-fa16-4f4b-b53b-3b4f511ae7e4": 5,
    # "53e9e9c1-5ff5-454f-aa4b-a0e3ca0a7408": 6,
    # "c15bd336-fe6f-46fa-91ab-7c2b7d919fc2": 7
  # }
# }

curl localhost:3000/drafts/72c71b09-5dca-4440-ac3d-6a0348908954/seats/53e9e9c1-5ff5-454f-aa4b-a0e3ca0a7408/pack

exit
curl -X POST localhost:3000/drafts/72c71b09-5dca-4440-ac3d-6a0348908954/seats/5b312a5c-28e8-42de-a859-f4666de3e4ca/pick -d card="Memory Spirit"

exit




exit

curl -X GET localhost:3000/drafts/list

exit

