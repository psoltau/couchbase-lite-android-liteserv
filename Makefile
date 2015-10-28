delete:
	curl -X DELETE http://192.168.57.101:5984/skybook/

db:
	curl -X PUT http://192.168.57.101:5984/skybook/

doc1:
	curl -X POST -H "Content-type: application/json" http://192.168.57.101:5984/skybook/ --data '{"type":"order","SeatNumber":"10"}'

doc2:
	curl -X POST -H "Content-type: application/json" http://192.168.57.101:5984/skybook/ --data '{"type":"user","Name":"ali"}'

filters:
	curl -X PUT -H "Content-type: application/json" http://192.168.57.101:5984/skybook/_design/orders --data '{"filters": {"order": "function(doc) {if(doc.type && doc.type == \"order\") {return true;}else{return false}}"}}'

changes:
	curl -X GET "http://192.168.57.101:5984/skybook/_changes?filter=_view&view=orders/order&since=0"
	#curl -X GET "http://192.168.57.101:5984/skybook/_changes?filter=_view&view=orders/order&since=0"
