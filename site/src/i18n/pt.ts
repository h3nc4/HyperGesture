// Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
//
// This file is part of HyperGesture.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

import { facts, formatSize, links } from "../facts";
import type { Strings } from "./en";

// The app itself is English only. Its button and card names stay in English here.
const path = (text: string) => `<span class="path">${text}</span>`;

export const pt: Strings = {
  lang: "pt-BR",
  locale: "pt-BR",
  home: "/pt/",
  title: "HyperGesture: navegação por gestos no HyperOS, com qualquer launcher",
  description:
    "App gratuito e de código aberto que traz de volta os gestos de Voltar, Início e Recentes ao HyperOS quando você usa Nova, Niagara, Lawnchair ou qualquer outro launcher. Funciona sem root, ADB ou computador.",
  skip: "Pular para o conteúdo",
  brandLabel: "Página inicial do HyperGesture",
  nav: { label: "Seções", install: "Instalar", privacy: "Privacidade", github: "GitHub" },
  language: { label: "Idioma", current: "Português" },

  hero: {
    title: "Seu launcher, seus gestos",
    lede: "O HyperOS desliga os gestos quando você troca de launcher. O HyperGesture devolve os gestos sem root, ADB ou computador.",
    download: "Baixar APK",
    downloadFacts: `v${facts.version}, ${formatSize(facts.apkBytes, "pt-BR")}, Android ${facts.minAndroid}+`,
    howTo: "Como instalar",
    qrLabel: "Código QR que abre o download do APK",
    qrText: "Está no computador? Aponte a câmera do celular para abrir o download nele.",
    caption: "O gesto lateral mostra a mesma curva escura com seta do Voltar do próprio HyperOS.",
  },

  gestures: [
    { name: "Voltar", how: "Deslize para dentro a partir da borda esquerda ou direita" },
    { name: "Início", how: "Deslize para cima a partir da borda inferior" },
    { name: "Apps recentes", how: "Deslize para cima a partir da borda inferior e segure" },
    { name: "App anterior", how: "Deslize para a direita ao longo da borda inferior" },
    { name: "Próximo app", how: "Deslize para a esquerda ao longo da borda inferior" },
  ],

  facts: {
    version: "Versão",
    size: "Tamanho",
    needs: "Requer",
    needsValue: `Android ${facts.minAndroid} ou mais recente`,
    license: "Licença",
    licenseValue: "Gratuito, GPL-3.0-or-later",
    cert: "SHA-256 do certificado de assinatura",
    affiliation: "Afiliação",
    affiliationValue: `Sem vínculo com a Xiaomi. <a href="${links.repo}">Código no GitHub</a>`,
  },

  why: {
    title: "O HyperOS desligou seus gestos. Este app liga de novo.",
    big: "O HyperOS prende os gestos de tela cheia ao launcher padrão. Troque para Nova, Niagara ou Lawnchair e o celular volta para os três botões.",
    body: `O HyperGesture é um app gratuito e de código aberto que traz os gestos de volta. Ele funciona sem root, ADB, Shizuku ou computador, e também funciona em qualquer celular com Android ${facts.minAndroid} ou mais recente.`,
    limitsTitle: "O que ele não consegue fazer",
    limits:
      "Ele não consegue ligar a navegação por gestos do próprio HyperOS, porque só o sistema pode fazer isso. Ele abre as configurações de navegação para você esconder a barra de três botões. Se o HyperOS redefinir o modo de navegação depois de reiniciar, o app avisa, mas não consegue mudar de volta.",
  },

  moves: {
    title: "Gestos de Voltar, Início e Recentes para o HyperOS",
    optional: "Ative App switching",
    note: "Puxe o dedo de volta antes de soltar e nada acontece. Todas as distâncias e tempos podem ser ajustados em Gesture settings.",
  },

  install: {
    title: "Instalar",
    intro: "O HyperGesture não está na Play Store, então o Android faz algumas perguntas a mais na primeira vez. Leva uns cinco minutos.",
    more: `Prefere atualizações automáticas? <a href="${links.obtainium}">Adicione ao Obtainium</a> ou veja todas as versões na <a href="${links.releases}">página de releases</a>.`,
    optional: "(opcional)",
    steps: [
      {
        title: "Baixe o APK",
        body: [
          "Toque em Baixar no celular. Se o navegador pedir, permita que ele instale apps desconhecidos e depois abra <code>hypergesture.apk</code>.",
        ],
      },
      {
        title: "Passe pelo Play Protect",
        body: ["O HyperGesture não está na Play Store, então o Google ainda não conhece o app. Talvez apareça uma destas telas."],
        dialogs: [
          { name: "Verificação de app recomendada", action: "Toque em Verificar app." },
          { name: "Enviar app para verificação de segurança", action: "Toque em Enviar desta vez ou Não enviar." },
          {
            name: "App bloqueado para proteger seu dispositivo",
            action:
              "Aparece no Brasil, na Índia e em outros mercados para apps com serviço de acessibilidade. Desligue o Play Protect durante a instalação. Abra a Play Store, toque na sua foto de perfil, depois em Play Protect e na engrenagem de configurações, e desative Verificar apps com o Play Protect. Instale o HyperGesture e depois ative a verificação de novo.",
          },
        ],
      },
      {
        title: "Permita as configurações restritas",
        body: [
          "O Android bloqueia o acesso de acessibilidade para apps instalados pelo navegador até você permitir. Tente o passo 4 uma vez antes, para o Android mostrar a opção.",
          `Depois ${path("Configurações &gt; Aplicativos &gt; HyperGesture &gt; menu &gt; Permitir configurações restritas")}`,
        ],
      },
      {
        title: "Ative o serviço",
        body: [
          path("Configurações &gt; Acessibilidade &gt; HyperGesture &gt; ativar"),
          "Abra o HyperGesture. O primeiro cartão deve mostrar Active. Teste um gesto.",
        ],
      },
      {
        title: "No HyperOS, mantenha o app rodando",
        body: [
          "No cartão Keep me running, toque em Autostart e ative o início automático. Depois toque em App info, abra Economia de bateria e escolha Sem restrições, o que deixa o app rodar em segundo plano.",
        ],
      },
      {
        title: "Trocar de app",
        optional: true,
        body: ["Ative o gesto lateral na borda inferior, toque em Grant e permita o Acesso ao uso."],
      },
    ],
  },

  privacy: {
    title: "Privacidade",
    strips: "Ele só presta atenção em faixas finas nas bordas esquerda, direita e inferior.",
    claim: "O HyperGesture não tem permissão de internet, então não consegue enviar nada para lugar nenhum.",
    body: [
      "Os gestos precisam de um serviço de acessibilidade, uma permissão poderosa. Este só observa toques em faixas finas nas bordas da tela e transforma um deslize em Voltar, Início, Recentes ou na troca para outro app.",
      "Ele nunca lê o que está na tela, o que você digita nem as teclas que você aperta. Com a troca de apps ativada, ele lê quais apps você usou nas últimas seis horas, e essa lista fica só na memória do seu celular.",
      "Assim como o app, este site funciona sem anúncios, rastreadores ou conta.",
      `O código é público sob a GPL-3.0, e cada versão é compilada a partir dele. <a href="${links.repo}">Leia o código</a>.`,
    ],
  },


  footer: {
    blurb: `Gratuito e de código aberto, feito com <a href="${links.contributors}">seus colaboradores</a>. <a href="mailto:me@h3nc4.com">me@h3nc4.com</a>`,
    project: "Projeto",
    source: "Código-fonte",
    releases: "Versões",
    report: "Relatar um problema",
    license: "Licença GPL-3.0-or-later. Nenhum rastreador neste site.",
    trademarks:
      "Xiaomi, HyperOS e MIUI são marcas registradas da Xiaomi. O HyperGesture não tem vínculo com a Xiaomi. Android é uma marca registrada da Google LLC.",
  },

  notFound: {
    title: "Página não encontrada",
    body: "Esta página não existe.",
    back: "Ir para a página inicial do HyperGesture",
  },
};
