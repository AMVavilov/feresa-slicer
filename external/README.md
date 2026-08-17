# External source trees

The OrcaSlicer source tree is intentionally not committed inside the workspace
repository. `scripts/fetch-orcaslicer.sh` checks out the pinned upstream commit
into `external/orcaslicer/`, which is ignored by Git.

No proprietary Bambu networking plugin binaries are fetched by the script.
