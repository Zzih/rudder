import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
// 仅注册项目实际用到的图标(由 grep <el-icon><X/></el-icon> + admin 菜单 icon: 'X' 得出),
// 比 import * 全量 200+ bundle 减少 ~75%。新增图标用法时务必同步本白名单,否则模板渲染为空
import {
  ArrowDown, ArrowLeft, ArrowRight, Bell, Calendar, Camera, ChatDotRound,
  Check, CircleCheck, CircleClose, Clock, Close, Coin, Connection, Cpu,
  DataAnalysis, Delete, Document, DocumentAdd, Edit, Eleme, Files, Folder,
  FolderAdd, FolderChecked, FolderOpened, FullScreen, Grid, InfoFilled, Key,
  Link, Loading, Lock, MagicStick, Monitor, Moon, MoreFilled, OfficeBuilding,
  Plus, Promotion, Rank, Refresh, Right, Search, SetUp, Setting, Share, Stamp,
  Star, Sunny, SwitchButton, Timer, Tools, Upload, User, VideoPause,
  VideoPlay, ZoomIn, ZoomOut,
} from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import i18n from './locales'
import './styles/theme.scss'
import './styles/global.scss'
import 'highlight.js/styles/github.css'

const app = createApp(App)

const icons = {
  ArrowDown, ArrowLeft, ArrowRight, Bell, Calendar, Camera, ChatDotRound,
  Check, CircleCheck, CircleClose, Clock, Close, Coin, Connection, Cpu,
  DataAnalysis, Delete, Document, DocumentAdd, Edit, Eleme, Files, Folder,
  FolderAdd, FolderChecked, FolderOpened, FullScreen, Grid, InfoFilled, Key,
  Link, Loading, Lock, MagicStick, Monitor, Moon, MoreFilled, OfficeBuilding,
  Plus, Promotion, Rank, Refresh, Right, Search, SetUp, Setting, Share, Stamp,
  Star, Sunny, SwitchButton, Timer, Tools, Upload, User, VideoPause,
  VideoPlay, ZoomIn, ZoomOut,
}
for (const [key, component] of Object.entries(icons)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(i18n)
app.use(ElementPlus)
app.mount('#app')
