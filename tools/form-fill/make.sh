#!/bin/bash
# 빈 양식에 초안을 채워 contest-application-consent.docx를 다시 만든다. 사용: tools/form-fill/make.sh
set -e; cd "$(dirname "$0")/../.."; W=$(mktemp -d); mkdir "$W/x"; (cd "$W/x" && unzip -q "$OLDPWD/contest-application-consent.blank.docx")
python3 tools/form-fill/fill_form.py "$W/x/word/document.xml" docs/plan/application-draft.md
(cd "$W/x" && zip -q -r ../out.docx . -x '.*'); cp "$W/out.docx" contest-application-consent.docx; rm -rf "$W"
