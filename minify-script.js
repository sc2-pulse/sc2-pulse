const { minify } = require("terser");
const fs = require('fs');
const path = require('path');

const root = 'target/classes/js';
const orderedFiles = [
    root + '/LuxonConfig.js',
    root + '/IntervalExecutor.js',
    root + '/SortParameter.js',
    root + '/Cursor.js',
    root + '/Util.js',
    root + '/BootstrapUtil.js',
    root + '/ElementUtil.js',
    root + '/enum.js',
    root + '/EnumUtil.js',
    root + '/CharacterUtil.js',
    root + '/FollowUtil.js',
    root + '/HistoryUtil.js',
    root + '/LadderUtil.js',
    root + '/Model.js',
    root + '/Pagination.js',
    root + '/PaginationUtil.js',
    root + '/SeasonUtil.js',
    root + '/MetaUtil.js',
    root + '/Session.js',
    root + '/SC2Restful.js',
    root + '/ChartUtil.js',
    root + '/StatsUtil.js',
    root + '/TableUtil.js',
    root + '/TeamUtil.js',
    root + '/ViewUtil.js',
    root + '/FormUtil.js',
    root + '/ClanUtil.js',
    root + '/Buffer.js',
    root + '/BufferUtil.js',
    root + '/MatchUtil.js',
    root + '/VersusUtil.js',
    root + '/VODUtil.js',
    root + '/RevealUtil.js',
    root + '/GroupUtil.js',
    root + '/CommunityUtil.js',
    root + '/MatrixUI.js',
    root + '/EnhancementUtil.js'
];

const fileContents = orderedFiles.reduce((acc, filePath) => {
  acc[path.basename(filePath)] = fs.readFileSync(filePath, 'utf8');
  return acc;
}, {});

minify(fileContents, {
    compress: true,
    mangle: true
})
.then(result => {
    const outputDir = 'target/sc2-webapp/static/script';
    if (!fs.existsSync(outputDir)) {
      fs.mkdirSync(outputDir, { recursive: true });
    }
    fs.writeFileSync(outputDir + '/sc2-restful.min.js', result.code);
})
.catch(error => {
  console.error(error);
});
