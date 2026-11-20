# Сборка и запуск FRONT (без изменений в его коде).
# FRONT написан на React 17 и node-sass 4, поэтому нужна именно Node 14.

FROM node:14-bullseye AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .

# Адрес REST-сервиса зашивается в приложение при сборке. Его открывает браузер, поэтому это адрес на вашем ПК.
ARG REACT_APP_API_URL=http://localhost:8080
ENV REACT_APP_API_URL=$REACT_APP_API_URL
# В package.json указан homepage для GitHub Pages; PUBLIC_URL=/ заставляет отдавать приложение из корня
ENV PUBLIC_URL=/
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/build /usr/share/nginx/html
EXPOSE 80
