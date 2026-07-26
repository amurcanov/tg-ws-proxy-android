<div align="center">
  
  # Telegram WS Proxy Android
<br>
  <img src="https://img.shields.io/badge/Android-SDK_24--36-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android SDK">
  <img src="https://img.shields.io/badge/Rust-1.70+-000000?style=for-the-badge&logo=rust&logoColor=white" alt="Rust Version">
  <img src="https://img.shields.io/badge/Kotlin-Native-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin">
  <a href="https://github.com/amurcanov/tg-ws-proxy-android/stargazers">
    <img src="https://img.shields.io/github/stars/amurcanov/tg-ws-proxy-android?style=for-the-badge&logo=github&color=ffca28&labelColor=24292e" alt="Stars">
  </a>
</div>
<br>

<div dir="rtl">

**TG WS Proxy Android** یه **پروکسی MTProto** محلی برای تلگرام روی اندرویده. این برنامه کمک می‌کنه یه سری مشکل اتصال رو تا حدی حل کنی و تو بعضی حالت‌ها سرعت پیام‌رسان رو بهتر می‌کنه؛ کارش اینه که ترافیک رو یا از مسیر امن وب‌سوکت پشت **CloudFlare** رد می‌کنه یا مستقیم می‌فرسته سمت دیتاسنترهای تلگرام.

</div>

---

<img width="972" height="696" alt="MyCollages (5)" src="https://github.com/user-attachments/assets/7c9b9f2a-fc60-4aee-b93d-db950e24555c" />

<div dir="rtl">

## امکانات نسخه اندروید

- **رابط کاربری امروزی:** برنامه کامل با ظاهر جدید اندروید جوره؛ روی Material 3 و Jetpack Compose ساخته شده. کارهای اصلی سریع در دسترسن و خبری از صفحه‌های شلوغ نیست.
- **یکپارچگی با تلگرام:** دکمه‌ی **«اعمال در تلگرام»** خودش پروکسی رو با `tg://proxy` می‌فرسته به کلاینت‌های سازگار (AyuGram، Plus Messenger، NekoGram و بقیه).
- **کار در پس‌زمینه:** از `Foreground Service`، اعلان وضعیت سرویس و یه سری منطق اضافه برای نگه‌داشتن اتصال استفاده می‌کنه تا اندروید خیلی راحت پروکسی رو نبنده.
- **نمایش لاگ:** لاگ رویدادها رو همون لحظه نشونت می‌ده تا زود بفهمی سر اتصال، مسیر و استخر اتصال‌ها چه خبره.
- **پوسته و پالت رنگ:** روی اندروید ۱۲ به بالا Dynamic Colors داره و برای دستگاه‌های قدیمی‌تر هم پالت‌های آماده گذاشته شده.
- **آپدیت خودکار داخل برنامه:** دیگه لازم نیست دستی دنبال نسخه جدید بگردی؛ هر وقت نسخه تازه بیاد، خود برنامه بهت خبر می‌ده.
- **بخش «اطلاعات»:** داخل برنامه یه راهنمای کامل هست درباره تنظیمات، جزئیات CloudFlare، استخر اتصال‌های WS و تنظیم دستی دیتاسنترها.

</div>

---

<div dir="rtl">

## چطوری کار می‌کنه

</div>

```text
Telegram Android → MTProto محلی (پیش‌فرض 127.0.0.1:1443) → TG WS Proxy → WSS (از طریق CloudFlare یا مستقیم) → دیتاسنتر تلگرام
```

<div dir="rtl">

1. برنامه با یه موتور نیتیو نوشته‌شده با **Rust** یه پروکسی MTProto محلی بالا میاره.
2. اتصال‌های تلگرام رو از طریق پورت محلی و یه کلید محرمانه‌ی ساخته‌شده می‌گیره.
3. `DC ID` رو از بسته‌ی اولیه درمیاره و یه اتصال امن وب‌سوکت (`TLS`) به دیتاسنتر مورد نظر برقرار می‌کنه و اگه لازم شد ترافیک رو از CloudFlare رد می‌کنه.
4. برای اینکه تو شرایط واقعی شبکه پایدارتر کار کنه، از استخر اتصال، مکانیزم keepalive و مسیرهای جایگزین (fallback) استفاده می‌کنه.

## شروع سریع

1. آخرین `APK` رو از **[صفحه‌ی نسخه‌ها](https://github.com/amurcanov/tg-ws-proxy-android/releases)** دانلود کن.
2. برنامه رو روی گوشی اندرویدت نصب کن.
3. **TG WS Proxy Android** رو باز کن.
4. یه سر به راهنمای داخل برنامه بزن.
5. **«اجرای پروکسی»** رو بزن — یه اعلان می‌گه که تو پس‌زمینه داره کار می‌کنه.
6. **«اعمال در تلگرام»** رو بزن — کلاینت تلگرام باز می‌شه و فقط کافیه اتصال رو تأیید کنی.

</div>

---

# 🎦 ویدیوی راهنمای نصب و استفاده

<div align="center">

<img width="1376" height="768" alt="578516258-6b2df494-de8d-44a2-a281-389fc7551a7c" src="https://github.com/user-attachments/assets/ed1449d4-0a14-4b46-8f35-b787bdee3e32" />

<br><br>

[**تماشا در یوتیوب**](https://youtu.be/RP4RwyEHpwc) | [**تماشا در تلگرام**](https://t.me/avencoreschat/506796)

</div>

---

<div dir="rtl">

* **کرش و مشکل نصب:** اگه موقع نصب کرش، بسته‌شدن ناگهانی یا خطا گرفتی، لطفاً گزارش‌ها و لینکشون رو نگه دار. همینطور بلوک `NOTE` پایین رو بخون و یه `issue` درست‌وحسابی با اطلاعات فنی مفید باز کن.

</div>

> [!NOTE]
> ### گزارش خطاها
> برنامه برای شبکه‌های موبایل تنظیم شده، ولی هنوز ممکنه به‌خاطر محدودیت‌های سیستمی یا خود شبکه، تو کار پس‌زمینه مشکل پیش بیاد.
>
> اگه به مشکل، کرش یا سؤالی خوردی، لطفاً دکمه‌ی **«ساخت گزارش»** داخل برنامه رو بزن و داده‌هاش رو به `issue`ت بچسبون. خطاهای ریز تو لاگ وقتی پروکسی درست کار می‌کنه رو می‌شه نادیده گرفت.

---

<div dir="rtl">

## مجوز

این فورک تحت مجوز **GPLv3** منتشر می‌شه. کد اصلی `tg-ws-proxy` از [Flowseal](https://github.com/Flowseal) هم تحت مجوز **MIT** در دسترسه.

</div>
