# Preparing the GitHub Wiki

[User guide](EN-Home.md) · [Technical documentation](../README.md)

The maintained source is in [docs/wiki](Home.md). Repository Markdown is available to readers with access to the repository. Wiki availability depends on repository visibility and the GitHub plan. [GitHub Wiki documentation](https://docs.github.com/en/communities/documenting-your-project-with-wikis/about-wikis).

## Validate and export

Run these commands from the source repository with Python 3.11 or newer:

```sh
python scripts/prepare-wiki.py --check
python scripts/prepare-wiki.py --output interface/.cache/wiki-export
```

The checker verifies local targets, anchors and image alt text. The export preserves Home, sidebar and footer, copies referenced images, and maps links to Wiki pages or to the named source ref. Specify `--repository OWNER/REPO` and `--ref REF` when needed. Use a fresh output directory after removing a source page. The generated manifest and safety backups stay local.

## Update screenshots

Run `node interface/scripts/capture-docs.mjs` for the synthetic Windows interface screens. For the first-run and ZIP screens, use `node interface/scripts/capture-archive-docs.mjs <EXE-path> docs/wiki/assets` with the selected Windows build. Set `TRACKER_BUILD_PYTHON` if the project Python interpreter is outside PATH.

## Publish when the Wiki is available

Copy the prepared Markdown and assets into the Wiki checkout and review the diff and links before publishing. Push the source ref referenced by the images so readers with repository access can see them. Keep private archives, live account records and diagnostic output out of the Wiki. Read through the Turkish, English, Spanish and Brazilian Portuguese entry pages after a structural change.
