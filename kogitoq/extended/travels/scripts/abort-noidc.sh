#!/bin/bash
abortCode="${2:-abortsignal}"
port="${3:-8580}"
bookingNumber=123
id=$1 
TOKEN=`./gettoken-prod.sh`
echo ''
echo $TOKEN
echo "MessageCode3 passed is $messageCode - testing BaseEntity"
echo ''
curl   -H "Content-Type: application/json"  -H "Accept: application/json" -X POST http://alyson2.genny.life:${port}/travels/${id}/${abortCode} -d ''
echo "Done..!!"
