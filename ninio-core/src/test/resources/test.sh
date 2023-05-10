openssl genrsa -out ca.key 2048
openssl req -x509 -new -nodes -key ca.key -sha256 -days 3650 -out ca.crt

openssl genrsa -out server.key 2048
openssl genrsa -out client.key 2048

openssl req -new -key server.key -out server.csr
openssl req -new -key client.key -out client.csr

openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out server.crt -days 3650 -sha256
openssl x509 -req -in client.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out client.crt -days 3650 -sha256

keytool -importcert -alias ca -file ca.crt -keystore client.jks -storepass storepassword
keytool -importcert -alias client_public -file client.crt -keystore client.jks -storepass storepassword
keytool -import -alias client_private -file client.key -keystore client.jks -storepass storepassword

keytool -importcert -alias ca -file ca.crt -keystore server.jks -storepass storepassword
keytool -importcert -alias server_public -file server.crt -keystore server.jks -storepass storepassword
keytool -import -alias server_private -file server.key -keystore server.jks -storepass storepassword
