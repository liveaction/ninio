keytool -genkeypair -alias ca -dname "cn=Local Network - Development" -validity 10000 -keyalg RSA -keysize 2048 -ext bc:c -keystore ca.jks -keypass password -storepass password
keytool -exportcert -rfc -keystore ca.jks -alias ca -storepass password > ca.pem


keytool -genkeypair -alias server -dname cn=server -validity 10000 -keyalg RSA -keysize 2048 -keystore server.jks -keypass password -storepass password

keytool -keystore server.jks -storepass password -certreq -alias server \
| keytool -keystore ca.jks -storepass password -gencert -alias ca -ext ku:c=dig,keyEnc -ext "san=dns:localhost,ip:192.1.1.18" -ext eku=sa,ca -rfc > server.pem

keytool -keystore server.jks -storepass password -importcert -alias ca -file ca.pem
keytool -keystore server.jks -storepass password -importcert -alias server -file server.pem


keytool -genkeypair -alias client -dname cn=server -validity 10000 -keyalg RSA -keysize 2048 -keystore client.jks -keypass password -storepass password

keytool -keystore client.jks -storepass password -certreq -alias client \
| keytool -keystore ca.jks -storepass password -gencert -alias ca -ext ku:c=dig,keyEnc -ext "san=dns:localhost,ip:192.1.1.18" -ext eku=sa,ca -rfc > client.pem

keytool -keystore client.jks -storepass password -importcert -alias ca -file ca.pem
keytool -keystore client.jks -storepass password -importcert -alias client -file client.pem

