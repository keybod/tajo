select col0, col1, col2, col3, sum(col4) as total, avg(col5) from base group by col0, cube (col1, col2), rollup(col3) having total > 100