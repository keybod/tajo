create external table testOuterJoinAndCaseWhen1 (id int, name text, score float, type text) using text
with ('text.delimiter'='|', 'text.null'='NULL') location ${table.path};

