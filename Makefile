delete:
	curl -X DELETE http://192.168.57.101:5984/skybook/

db:
	curl -X PUT http://192.168.57.101:5984/skybook/

doc1:
	curl -X POST -H "Content-type: application/json" http://192.168.57.101:5984/skybook/ --data '{"type":"order","SeatNumber":"10"}'

doc2:
	curl -X POST -H "Content-type: application/json" http://192.168.57.101:5984/skybook/ --data '{"type":"user","Name":"tom"}'

filters1:
	curl -X PUT -H "Content-type: application/json" http://192.168.57.101:5984/skybook/_design/orders --data '{"filters": {"order": "function(doc, req) {console.log(req);if(doc.type && doc.type == \"order\") {return true;}else{return false}}"}}'

filters2:
	curl -X PUT -H "Content-type: application/json" http://192.168.57.101:5984/skybook/_design/orders2 --data '{"filters": {"order": "function(doc, req) {console.log(req);if(doc.type && doc.type == \"order\" && req.abc == 1) {return true;}else{return false}}"}}'

changes1:
	curl -X GET "http://192.168.57.101:5984/skybook/_changes?filter=orders/order&since=0"

changes2:
	curl -X GET "http://192.168.57.101:5984/skybook/_changes?filter=orders/order2&since=0&abc=1"

changes3:
	curl -X GET "http://192.168.57.101:5984/skybook/_changes?filter=orders/order2&since=0&abc=2"
