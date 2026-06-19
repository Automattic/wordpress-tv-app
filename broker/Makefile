# Deploy the broker to Fly.io.
#
# Secrets live in the gitignored `fly.secrets` file (name=value, one per line;
# see fly.secrets.example) and are pushed into the Fly app on deploy.
.PHONY: deploy secrets

# Push secrets from fly.secrets into the Fly app. Run on its own when they change.
secrets:
	fly secrets import < fly.secrets

# Load secrets from the file, then deploy.
# --ha=false → a single machine. The session store is in-memory, so a second
# machine would split the rendezvous (POST /session and the poll could hit
# different machines).
deploy: secrets
	fly deploy --ha=false
