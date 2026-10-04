"""Create a one-off, short-lived LAN test CA/leaf without changing trust stores.

The CA private key is never saved, so it cannot issue additional certificates.
All output is confined to ignored target/a2-local-tls, outside frontend assets.
Requires the already-bundled cryptography package. Not a production PKI tool.
"""
import argparse
import ipaddress
import json
from datetime import datetime, timedelta, timezone
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import ExtendedKeyUsageOID, NameOID


def prepare(host: str) -> None:
    address = ipaddress.IPv4Address(host)
    private_ranges = [ipaddress.ip_network(value) for value in ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")]
    if not any(address in network for network in private_ranges):
        raise ValueError("Only an explicit RFC1918 LAN IPv4 address is allowed")
    output = Path(__file__).resolve().parents[1] / "target" / "a2-local-tls"
    if output.exists():
        raise FileExistsError(f"Refusing to overwrite existing certificates: {output}")
    now = datetime.now(timezone.utc)
    expiry = now + timedelta(days=7)
    ca_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    leaf_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    ca_name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, f"2G A2 Local Test {now:%Y%m%d-%H%M}")])
    ca = (x509.CertificateBuilder().subject_name(ca_name).issuer_name(ca_name)
          .public_key(ca_key.public_key()).serial_number(x509.random_serial_number())
          .not_valid_before(now - timedelta(minutes=5)).not_valid_after(expiry)
          .add_extension(x509.BasicConstraints(ca=True, path_length=0), critical=True)
          .add_extension(x509.KeyUsage(False, False, False, False, False, True, True, False, False), critical=True)
          .add_extension(x509.SubjectKeyIdentifier.from_public_key(ca_key.public_key()), critical=False)
          .sign(ca_key, hashes.SHA256()))
    leaf = (x509.CertificateBuilder()
            .subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, host)]))
            .issuer_name(ca_name).public_key(leaf_key.public_key()).serial_number(x509.random_serial_number())
            .not_valid_before(now - timedelta(minutes=5)).not_valid_after(expiry)
            .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
            .add_extension(x509.SubjectAlternativeName([x509.IPAddress(address)]), critical=False)
            .add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]), critical=False)
            .add_extension(x509.KeyUsage(True, False, True, False, False, False, False, False, False), critical=True)
            .add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key(ca_key.public_key()), critical=False)
            .sign(ca_key, hashes.SHA256()))
    output.mkdir(parents=True)
    (output / "server.key").write_bytes(leaf_key.private_bytes(
        serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    (output / "server.pem").write_bytes(leaf.public_bytes(serialization.Encoding.PEM))
    (output / "a2-test-ca.pem").write_bytes(ca.public_bytes(serialization.Encoding.PEM))
    (output / "a2-test-ca.cer").write_bytes(ca.public_bytes(serialization.Encoding.DER))
    metadata = {
        "host": host, "caName": ca_name.get_attributes_for_oid(NameOID.COMMON_NAME)[0].value,
        "expiresAt": expiry.isoformat(), "sha256": ca.fingerprint(hashes.SHA256()).hex().upper(),
        "caPrivateKeySaved": False,
    }
    (output / "metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    print(json.dumps(metadata, indent=2))
    print(f"Created local test material in {output}; no certificate was installed or trusted.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", required=True)
    prepare(parser.parse_args().host)
