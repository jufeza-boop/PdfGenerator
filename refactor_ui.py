import os

def replace_in_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Common model replacements
    content = content.replace('ProjectWithBlocks', 'ProjectData')
    content = content.replace('ContentBlockEntity', 'BlockData')
    content = content.replace('VisitEntity', 'VisitData')
    
    # Types
    content = content.replace('Long?', 'String?')
    content = content.replace('(Long)', '(String)')
    content = content.replace('Map<Long,', 'Map<String,')
    content = content.replace('Map<Long?,', 'Map<String?,')
    
    # Fields
    content = content.replace('.project.name', '.name')
    content = content.replace('.project.showHeaderBox', '.showHeaderBox')
    content = content.replace('.project.headerCompany', '.headerCompany')
    content = content.replace('.project.headerCompanySub', '.headerCompanySub')
    content = content.replace('.project.headerTitle', '.headerTitle')
    content = content.replace('.project.showHeaderLabel', '.showHeaderLabel')
    content = content.replace('.project.reportLabel', '.reportLabel')
    content = content.replace('.project.showHeaderDate', '.showHeaderDate')
    content = content.replace('.project.showHeaderTitle', '.showHeaderTitle')
    
    content = content.replace('.visitId', '.visitUuid')
    content = content.replace('visit.id', 'visit.uuid')
    content = content.replace('block.id', 'block.uuid')
    content = content.replace('project.project.id', 'project.uuid')
    content = content.replace('project.id', 'project.uuid')
    
    content = content.replace('mutableStateOf<Long?>', 'mutableStateOf<String?>')
    content = content.replace('mutableStateOf<com.example.data.VisitEntity?>', 'mutableStateOf<VisitData?>')
    content = content.replace('== 0L', '== ""')
    content = content.replace('onExportSingleVisit(0L)', 'onExportSingleVisit("")')
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

base_dir = r'c:\Users\jufez\Desktop\desarrolloIA\PdfGenerator\composeApp\src\commonMain\kotlin\com\example\ui'
for root, dirs, files in os.walk(base_dir):
    for file in files:
        if file.endswith('.kt'):
            replace_in_file(os.path.join(root, file))
print('Refactor applied.')
